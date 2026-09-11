# SPDX-License-Identifier: Apache-2.0
import logging
import time
import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session
from starlette.concurrency import run_in_threadpool

from app.api.schemas import (
    ApplyWorkflowRequest,
    ApproveWorkflowRequest,
    CVHealthResponse,
    DiseaseDetectionResponse,
    RejectWorkflowRequest,
    RetrieveRequest,
    WorkflowRunResponse,
)
from app.core.config import get_settings
from app.cv.contract import class_contract
from app.db.models.crop_season import CropSeason
from app.db.models.disease_report import DiseaseReport
from app.db.repositories.parcel_repository import (
    get_current_crop_season,
    get_parcel_by_code,
)
from app.db.session import get_db
from app.graph.graph import farm_graph
from app.graph.state import FarmState
from app.llm.recommendation_writer import GroundedRecommendationWriter
from app.rag.retriever import RAGRetriever
from app.services.diagnosis_service import diagnose_report
from app.services.plant_disease_service import PlantDiseaseService
from app.services.workflow_service import (
    WorkflowConflictError,
    apply_workflow_case,
    approve_workflow_case,
    create_workflow_case,
    get_case_by_request_id,
    get_workflow_case,
    reject_workflow_case,
    serialize_case,
)

router = APIRouter(prefix="/disease", tags=["Disease Detection"])
logger = logging.getLogger(__name__)
MAX_IMAGE_BYTES = 10 * 1024 * 1024


async def read_image(image: UploadFile) -> bytes:
    if not (image.content_type or "").startswith("image/"):
        raise HTTPException(400, "File uploaded must be an image.")
    data = await image.read(MAX_IMAGE_BYTES + 1)
    if len(data) > MAX_IMAGE_BYTES:
        raise HTTPException(413, "Image exceeds 10 MiB.")
    return data


def predictor(request: Request) -> PlantDiseaseService:
    model = getattr(request.app.state, "plant_model", None)
    if model is None:
        raise HTTPException(503, "Plant disease model is not available.")
    return PlantDiseaseService(model=model)


@router.get("/health", response_model=CVHealthResponse)
def health(request: Request):
    settings = get_settings()
    model = getattr(request.app.state, "plant_model", None)
    device = getattr(settings, "_chosen_device", None)
    return CVHealthResponse(
        status="ok" if model is not None else "not_ready",
        model_loaded=model is not None,
        model_name=settings.cv_model_name,
        model_version=settings.cv_model_version,
        device=str(device) if device else None,
    )


@router.get("/class-contract")
def get_class_contract():
    return class_contract()


@router.post("/predict", response_model=DiseaseDetectionResponse)
async def predict_disease(request: Request, image: Annotated[UploadFile, File()]):
    data = await read_image(image)
    try:
        return await run_in_threadpool(predictor(request).predict, data)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@router.post("/reports/{report_id}/diagnose")
async def diagnose(
    report_id: uuid.UUID,
    request: Request,
    image: Annotated[UploadFile, File()],
    db: Annotated[Session, Depends(get_db)],
):
    data = await read_image(image)
    try:
        return await run_in_threadpool(
            diagnose_report, db, report_id, data, predictor(request)
        )
    except LookupError as exc:
        raise HTTPException(404, str(exc)) from exc
    except FileExistsError as exc:
        raise HTTPException(409, str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@router.post("/knowledge/retrieve")
def retrieve(payload: RetrieveRequest, db: Annotated[Session, Depends(get_db)]):
    if not payload.query.strip():
        raise HTTPException(422, "query must not be blank")
    return RAGRetriever().retrieve(
        db, payload.query, top_k=payload.top_k, disease_code=payload.disease_code
    )


@router.post("/workflow/run", response_model=WorkflowRunResponse)
async def run_workflow(
    request: Request,
    image: Annotated[UploadFile, File()],
    db: Annotated[Session, Depends(get_db)],
    request_id: Annotated[str | None, Form()] = None,
    parcel_code: Annotated[str | None, Form()] = None,
    crop_season_id: Annotated[str | None, Form()] = None,
    rag_question: Annotated[str | None, Form()] = None,
    requested_by: Annotated[str | None, Form()] = None,
):
    data = await read_image(image)
    req_id = request_id or str(uuid.uuid4())
    existing = get_case_by_request_id(db, req_id)
    if existing is not None:
        payload = serialize_case(existing)
        return WorkflowRunResponse(
            case_id=payload["case_id"],
            request_id=req_id,
            approval_status=payload["approval_status"],
            application_status=payload["application_status"],
            recommendation=payload["recommendation"],
            response=payload,
            errors=[],
        )
    vision = predictor(request)

    try:
        if crop_season_id:
            season_uuid = uuid.UUID(crop_season_id)
            season = db.get(CropSeason, season_uuid)
            if season is None:
                raise LookupError("Crop season not found")
            if parcel_code and season.parcel.code != parcel_code:
                raise ValueError("Crop season does not belong to parcel")
            resolved_parcel_code = season.parcel.code
        else:
            if not parcel_code:
                raise ValueError("parcel_code or crop_season_id is required")
            parcel = get_parcel_by_code(db, parcel_code)
            if parcel is None:
                raise LookupError("Parcel not found")
            season = get_current_crop_season(db, parcel.id)
            if season is None:
                raise LookupError("Active crop season not found")
            season_uuid = season.id
            resolved_parcel_code = parcel.code
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    except LookupError as exc:
        raise HTTPException(404, str(exc)) from exc
    except SQLAlchemyError as exc:
        db.rollback()
        logger.exception("workflow_context_database_error request_id=%s", req_id)
        raise HTTPException(503, "Workflow database is unavailable") from exc

    report = DiseaseReport(
        crop_season_id=season_uuid,
        image_url=f"workflow://{req_id}",
        status="PENDING",
    )
    try:
        db.add(report)
        db.commit()
        db.refresh(report)
    except SQLAlchemyError as exc:
        db.rollback()
        logger.exception("workflow_report_database_error request_id=%s", req_id)
        raise HTTPException(503, "Workflow database is unavailable") from exc

    started = time.perf_counter()

    initial_state: FarmState = {
        "request_id": req_id,
        "parcel_code": resolved_parcel_code,
        "crop_season_id": str(season_uuid),
        "disease_report_id": str(report.id),
        "image_ref": data,
        "vision_tool": vision,
        "db": db,
        "rag_question": rag_question,
        "rag_top_k": 3,
        "recommendation_writer": GroundedRecommendationWriter(),
        "approval_status": "PENDING",
        "errors": [],
        "timings_ms": {},
    }

    try:
        final_state = await run_in_threadpool(farm_graph.invoke, initial_state)
        final_state.setdefault("timings_ms", {})["workflow_total"] = round(
            (time.perf_counter() - started) * 1000, 2
        )
        rec = final_state["recommendation"]
        case = create_workflow_case(
            db,
            request_id=req_id,
            crop_season_id=season_uuid,
            disease_report_id=report.id,
            disease_code=final_state["disease_code"],
            structured_path=rec["path"],
            recommendation=rec,
            rag_source_ids=rec.get("rag_sources", []),
            low_confidence=final_state.get("low_confidence", False),
            requested_by=requested_by,
        )
    except ValueError as exc:
        _mark_report_failed(db, report.id)
        raise HTTPException(400, str(exc)) from exc
    except SQLAlchemyError as exc:
        _mark_report_failed(db, report.id)
        logger.exception("workflow_database_error request_id=%s", req_id)
        raise HTTPException(503, "Workflow database is unavailable") from exc
    except Exception as exc:
        _mark_report_failed(db, report.id)
        logger.exception("workflow_execution_failed request_id=%s", req_id)
        raise HTTPException(500, "Workflow execution failed") from exc

    logger.info(
        "workflow_pending request_id=%s disease_code=%s confidence=%s "
        "structured_path=%s approval_status=PENDING model_version=%s timings_ms=%s",
        req_id,
        final_state["disease_code"],
        final_state["confidence"],
        rec["path"],
        (final_state["cv_result"] or {}).get("model_version"),
        final_state["timings_ms"],
    )

    return WorkflowRunResponse(
        case_id=str(case.id),
        request_id=final_state.get("request_id", req_id),
        approval_status=final_state.get("approval_status", "PENDING"),
        application_status=final_state.get("application_status"),
        cv_result=final_state.get("cv_result"),
        field_context=final_state.get("field_context"),
        treatment_rule=final_state.get("treatment_rule"),
        rag_context=final_state.get("rag_context"),
        recommendation=final_state.get("recommendation"),
        response=final_state.get("response"),
        errors=final_state.get("errors", []),
        timings_ms=final_state.get("timings_ms", {}),
    )


def _mark_report_failed(db: Session, report_id: uuid.UUID) -> None:
    """Best-effort failure audit without masking the original workflow error."""

    try:
        db.rollback()
        report = db.get(DiseaseReport, report_id)
        if report is not None:
            report.status = "FAILED"
            db.commit()
    except SQLAlchemyError:
        db.rollback()


def _transition_error(exc: Exception) -> HTTPException:
    if isinstance(exc, LookupError):
        return HTTPException(404, str(exc))
    if isinstance(exc, WorkflowConflictError):
        return HTTPException(409, str(exc))
    if isinstance(exc, ValueError):
        return HTTPException(422, str(exc))
    if isinstance(exc, SQLAlchemyError):
        return HTTPException(503, "Workflow database is unavailable")
    return HTTPException(500, "Workflow transition failed")


@router.get("/workflow/{case_id}")
def workflow_status(case_id: uuid.UUID, db: Annotated[Session, Depends(get_db)]):
    try:
        return serialize_case(get_workflow_case(db, case_id))
    except Exception as exc:
        raise _transition_error(exc) from exc


@router.post("/workflow/{case_id}/approve")
def approve_workflow(
    case_id: uuid.UUID,
    payload: ApproveWorkflowRequest,
    db: Annotated[Session, Depends(get_db)],
):
    try:
        case = approve_workflow_case(
            db,
            case_id,
            actor_id=payload.actor_id,
            selected_rule_id=payload.selected_rule_id,
            approved_dose=payload.approved_dose,
            confirmed_low_confidence=payload.confirmed_low_confidence,
        )
        return serialize_case(case)
    except Exception as exc:
        raise _transition_error(exc) from exc


@router.post("/workflow/{case_id}/reject")
def reject_workflow(
    case_id: uuid.UUID,
    payload: RejectWorkflowRequest,
    db: Annotated[Session, Depends(get_db)],
):
    try:
        case = reject_workflow_case(
            db, case_id, actor_id=payload.actor_id, reason=payload.reason
        )
        return serialize_case(case)
    except Exception as exc:
        raise _transition_error(exc) from exc


@router.post("/workflow/{case_id}/applied")
def confirm_applied(
    case_id: uuid.UUID,
    payload: ApplyWorkflowRequest,
    db: Annotated[Session, Depends(get_db)],
):
    try:
        case = apply_workflow_case(
            db,
            case_id,
            actor_id=payload.actor_id,
            actual_dose=payload.actual_dose,
            applied_at=payload.applied_at,
        )
        return serialize_case(case)
    except Exception as exc:
        raise _transition_error(exc) from exc
