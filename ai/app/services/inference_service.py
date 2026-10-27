# SPDX-License-Identifier: Apache-2.0
"""Stateless CV, RAG, and Backend-facts inference orchestration."""

from __future__ import annotations

from typing import TYPE_CHECKING, Any

import httpx

from app.api.schemas import (
    DiagnoseCvResult,
    DiagnoseRagEvidence,
    DiagnoseRecommendation,
    DiagnoseRequest,
    DiagnoseResponse,
    DiagnoseRuleFact,
    DiagnoseStructuredContext,
)
from app.clients.backend_client import BackendClient
from app.core.config import get_settings
from app.cv.policy import WORKFLOW_CONFIDENCE_THRESHOLD
from app.cv.triage import CvTriageError
from app.graph.context import WorkflowRuntime
from app.graph.graph import farm_graph

if TYPE_CHECKING:
    from sqlalchemy.orm import Session

    from app.rag.retriever import RAGRetriever
    from app.services.plant_disease_service import PlantDiseaseService

MAX_IMAGE_BYTES = 10 * 1024 * 1024


class ImageFetchError(RuntimeError):
    """The report image could not be loaded safely."""


async def fetch_image(image_url: str) -> bytes:
    """Load one Backend-provided image with the same limit as file uploads."""
    try:
        async with (
            httpx.AsyncClient(
                timeout=get_settings().backend_timeout_seconds,
                follow_redirects=False,
            ) as client,
            client.stream("GET", image_url) as response,
        ):
            response.raise_for_status()
            if not response.headers.get("content-type", "").startswith("image/"):
                raise ImageFetchError("image_url did not return an image")
            content_length = response.headers.get("content-length")
            if content_length and int(content_length) > MAX_IMAGE_BYTES:
                raise ImageFetchError("Image exceeds 10 MiB")

            image = bytearray()
            async for chunk in response.aiter_bytes():
                if len(image) + len(chunk) > MAX_IMAGE_BYTES:
                    raise ImageFetchError("Image exceeds 10 MiB")
                image.extend(chunk)
    except ImageFetchError:
        raise
    except (httpx.HTTPError, ValueError) as exc:
        raise ImageFetchError("Unable to load image_url") from exc

    if not image:
        raise ImageFetchError("image_url returned an empty image")
    return bytes(image)


def run_stateless_diagnose(
    *,
    payload: DiagnoseRequest,
    image_bytes: bytes,
    predictor: PlantDiseaseService,
    db: Session,
    backend_client: BackendClient,
    rag_retriever: RAGRetriever | None = None,
) -> DiagnoseResponse:
    """Run the existing LangGraph and adapt its result to Backend's DTO."""
    cv_result = DiagnoseCvResult.model_validate(predictor.predict(image_bytes))
    confidence_threshold = WORKFLOW_CONFIDENCE_THRESHOLD
    if cv_result.confidence < confidence_threshold:
        raise CvTriageError(
            "Kết quả nhận diện chưa đủ tin cậy "
            f"({cv_result.confidence:.1%}, yêu cầu tối thiểu {confidence_threshold:.1%}). "
            "Vui lòng chụp lại ảnh cận cảnh lá lúa, đủ sáng và rõ nét."
        )

    if rag_retriever is None:
        from app.rag.retriever import RAGRetriever

        rag_retriever = RAGRetriever()

    def load_structured_facts(disease_code: str) -> dict[str, Any]:
        context = backend_client.get_diagnosis_context(
            request_id=payload.request_id,
            disease_code=disease_code,
            crop_season_id=payload.crop_season_id,
        )
        rules = [rule.model_dump(mode="json") for rule in context.rules]
        return {"path": "treatment" if rules else "review_required", "rules": rules}

    def retrieve_rag(query: str, disease_code: str, top_k: int) -> list[dict]:
        return rag_retriever.retrieve(
            db=db,
            query=query,
            disease_code=disease_code,
            top_k=top_k,
        )

    result = farm_graph.invoke(
        {
            "request_id": payload.request_id,
            "parcel_code": payload.field_context.parcel_code,
            "crop_season_id": payload.crop_season_id,
            "image_ref": image_bytes,
            "cv_result": cv_result.model_dump(mode="json"),
            "field_context": payload.field_context.model_dump(mode="json"),
            "errors": [],
            "timings_ms": {},
        },
        context=WorkflowRuntime(
            vision_tool=predictor,
            treatment_tool=load_structured_facts,
            rag_tool=retrieve_rag,
        ),
    )

    cv_result = DiagnoseCvResult.model_validate(result.get("cv_result"))
    rag_evidence = [
        DiagnoseRagEvidence(
            content=chunk["content"],
            document_title=chunk.get("document_title"),
            chunk_index=chunk.get("chunk_index"),
            source_ids=chunk.get("source_ids", []),
            similarity=float(chunk.get("similarity", 0.0)),
        )
        for chunk in result.get("rag_context", [])
        if chunk.get("content")
    ]
    treatment = result.get("treatment_rule") or {}
    structured_context = DiagnoseStructuredContext(
        path=treatment.get("path", ""),
        rules=[
            DiagnoseRuleFact.model_validate(rule) for rule in treatment.get("rules", [])
        ],
    )
    recommendation = result.get("recommendation") or {}
    selected_rule_id = recommendation.get("selected_rule_id")
    known_rule_ids = {rule.rule_id for rule in structured_context.rules}
    if selected_rule_id is not None and selected_rule_id not in known_rule_ids:
        raise ValueError("Recommendation selected an unknown rule_id")
    if structured_context.path == "treatment" and selected_rule_id is None:
        raise ValueError("Treatment recommendation must select a rule_id")
    if structured_context.path == "review_required" and selected_rule_id is not None:
        raise ValueError("Review-required recommendation cannot select a rule_id")

    explanation = None
    if payload.include_explanation:
        from sqlalchemy.orm import Session

        from app.rag.rag_service import synthesize_answer

        if isinstance(db, Session) and db.in_transaction():
            db.rollback()
        explanation = synthesize_answer(
            f"Theo tài liệu, {cv_result.disease_name} có đặc điểm gì để kỹ sư đối chiếu với gợi ý của mô hình?",
            [item.model_dump() for item in rag_evidence],
            "farm_diagnosis",
            context={
                "disease_code": cv_result.disease_code,
                "disease_name": cv_result.disease_name,
                "growth_stage": (
                    payload.field_context.growth_stage
                    if payload.field_context.growth_stage
                    and len(payload.field_context.growth_stage) <= 120
                    else None
                ),
            },
        )

    return DiagnoseResponse(
        request_id=payload.request_id,
        cv_result=cv_result,
        rag_evidence=rag_evidence,
        structured_context=structured_context,
        recommendation=DiagnoseRecommendation(
            selected_rule_id=selected_rule_id,
            requires_engineer_approval=True,
        ),
        generated_explanation=explanation,
    )
