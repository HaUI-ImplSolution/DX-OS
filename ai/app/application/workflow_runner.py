"""Application use case for generating a pending workflow recommendation."""

import logging
import time
import uuid
from typing import Any

from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.db.models.crop_season import CropSeason
from app.db.models.disease_report import DiseaseReport
from app.db.repositories.parcel_repository import (
    get_current_crop_season,
    get_parcel_by_code,
)
from app.graph.context import WorkflowRuntime
from app.graph.graph import farm_graph
from app.graph.state import FarmState
from app.llm.recommendation_writer import GroundedRecommendationWriter
from app.services.workflow_service import (
    create_workflow_case,
    get_case_by_request_id,
    serialize_case,
)

logger = logging.getLogger(__name__)


class WorkflowRunner:
    """Coordinates request resolution, LangGraph, and durable case creation."""

    def __init__(self, db: Session, vision_factory: Any, graph: Any = farm_graph):
        self.db = db
        self.vision_factory = vision_factory
        self.graph = graph

    @staticmethod
    def build_initial_state(
        *,
        request_id: str,
        parcel_code: str,
        crop_season_id: str,
        disease_report_id: str,
        image_bytes: bytes,
        rag_question: str | None,
    ) -> FarmState:
        """Build only the business data shared by graph nodes."""

        return {
            "request_id": request_id,
            "parcel_code": parcel_code,
            "crop_season_id": crop_season_id,
            "disease_report_id": disease_report_id,
            "image_ref": image_bytes,
            "rag_question": rag_question,
            "rag_top_k": 3,
            "errors": [],
            "timings_ms": {},
        }

    def run(
        self,
        *,
        image_bytes: bytes,
        request_id: str,
        parcel_code: str | None,
        crop_season_id: str | None,
        rag_question: str | None,
        requested_by: str | None,
    ) -> dict[str, Any]:
        existing = get_case_by_request_id(self.db, request_id)
        if existing is not None:
            payload = serialize_case(existing)
            return {
                "case_id": payload["case_id"],
                "request_id": request_id,
                "approval_status": payload["approval_status"],
                "application_status": payload["application_status"],
                "recommendation": payload["recommendation"],
                "response": payload,
                "errors": [],
            }

        # Fail before creating a report when the CV service is unavailable.
        # The idempotency return above deliberately remains model-independent.
        vision_tool = self.vision_factory()
        season, resolved_parcel_code = self._resolve_context(
            parcel_code=parcel_code, crop_season_id=crop_season_id
        )
        report = self._create_report(season.id, request_id)
        initial_state = self.build_initial_state(
            request_id=request_id,
            parcel_code=resolved_parcel_code,
            crop_season_id=str(season.id),
            disease_report_id=str(report.id),
            image_bytes=image_bytes,
            rag_question=rag_question,
        )
        runtime = WorkflowRuntime(
            db=self.db,
            vision_tool=vision_tool,
            recommendation_writer=GroundedRecommendationWriter(),
        )
        started = time.perf_counter()
        try:
            final_state = self.graph.invoke(initial_state, context=runtime)
            timings = dict(final_state.get("timings_ms", {}))
            timings["workflow_total"] = round(
                (time.perf_counter() - started) * 1000, 2
            )
            final_state["timings_ms"] = timings
            recommendation = final_state["recommendation"]
            case = create_workflow_case(
                self.db,
                request_id=request_id,
                crop_season_id=season.id,
                disease_report_id=report.id,
                disease_code=final_state["disease_code"],
                structured_path=recommendation["path"],
                recommendation=recommendation,
                rag_source_ids=recommendation.get("rag_sources", []),
                low_confidence=final_state.get("low_confidence", False),
                requested_by=requested_by,
            )
        except Exception:
            self._mark_report_failed(report.id)
            raise

        logger.info(
            "workflow_pending request_id=%s disease_code=%s confidence=%s "
            "structured_path=%s approval_status=PENDING model_version=%s timings_ms=%s",
            request_id,
            final_state["disease_code"],
            final_state["confidence"],
            recommendation["path"],
            (final_state["cv_result"] or {}).get("model_version"),
            final_state["timings_ms"],
        )
        return {
            "case_id": str(case.id),
            "request_id": final_state.get("request_id", request_id),
            "approval_status": "PENDING",
            "application_status": None,
            "cv_result": final_state.get("cv_result"),
            "field_context": final_state.get("field_context"),
            "treatment_rule": final_state.get("treatment_rule"),
            "rag_context": final_state.get("rag_context"),
            "recommendation": recommendation,
            "response": final_state.get("response"),
            "errors": final_state.get("errors", []),
            "timings_ms": final_state.get("timings_ms", {}),
        }

    def _resolve_context(
        self, *, parcel_code: str | None, crop_season_id: str | None
    ) -> tuple[CropSeason, str]:
        if crop_season_id:
            season_uuid = uuid.UUID(crop_season_id)
            season = self.db.get(CropSeason, season_uuid)
            if season is None:
                raise LookupError("Crop season not found")
            if parcel_code and season.parcel.code != parcel_code:
                raise ValueError("Crop season does not belong to parcel")
            return season, season.parcel.code
        if not parcel_code:
            raise ValueError("parcel_code or crop_season_id is required")
        parcel = get_parcel_by_code(self.db, parcel_code)
        if parcel is None:
            raise LookupError("Parcel not found")
        season = get_current_crop_season(self.db, parcel.id)
        if season is None:
            raise LookupError("Active crop season not found")
        return season, parcel.code

    def _create_report(self, crop_season_id: uuid.UUID, request_id: str) -> DiseaseReport:
        report = DiseaseReport(
            crop_season_id=crop_season_id,
            image_url=f"workflow://{request_id}",
            status="PENDING",
        )
        try:
            self.db.add(report)
            self.db.commit()
            self.db.refresh(report)
            return report
        except SQLAlchemyError:
            self.db.rollback()
            raise

    def _mark_report_failed(self, report_id: uuid.UUID) -> None:
        """Best-effort failure audit without masking the original error."""

        try:
            self.db.rollback()
            report = self.db.get(DiseaseReport, report_id)
            if report is not None:
                report.status = "FAILED"
                self.db.commit()
        except SQLAlchemyError:
            self.db.rollback()
