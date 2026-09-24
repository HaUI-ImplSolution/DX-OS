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
from app.graph.context import WorkflowRuntime
from app.graph.graph import farm_graph

if TYPE_CHECKING:
    from sqlalchemy.orm import Session

    from app.llm.recommendation_writer import GroundedRecommendationWriter
    from app.rag.retriever import RAGRetriever
    from app.services.plant_disease_service import PlantDiseaseService

MAX_IMAGE_BYTES = 10 * 1024 * 1024
ALLOWED_IMAGE_CONTENT_TYPES = frozenset({"image/jpeg", "image/png"})


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
            content_type = response.headers.get("content-type", "").split(";", 1)[0]
            if content_type.lower() not in ALLOWED_IMAGE_CONTENT_TYPES:
                raise ImageFetchError("image_url must return a JPEG or PNG image")
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
    recommendation_writer: GroundedRecommendationWriter | None = None,
) -> DiagnoseResponse:
    """Run the existing LangGraph and adapt its result to Backend's DTO."""
    if rag_retriever is None:
        from app.rag.retriever import RAGRetriever

        rag_retriever = RAGRetriever()
    if recommendation_writer is None:
        from app.llm.recommendation_writer import GroundedRecommendationWriter

        recommendation_writer = GroundedRecommendationWriter()

    def load_structured_facts(disease_code: str) -> dict[str, Any]:
        context = backend_client.get_diagnosis_context(
            request_id=payload.request_id,
            disease_code=disease_code,
            crop_season_id=payload.crop_season_id,
        )
        rules = [rule.model_dump(mode="json") for rule in context.rules]
        return {"rules": rules}

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
            "field_context": payload.field_context.model_dump(mode="json"),
            "errors": [],
            "timings_ms": {},
        },
        context=WorkflowRuntime(
            vision_tool=predictor,
            treatment_tool=load_structured_facts,
            rag_tool=retrieve_rag,
            recommendation_writer=recommendation_writer,
        ),
    )

    cv_result = DiagnoseCvResult.model_validate(result.get("cv_result"))
    rag_evidence = [
        DiagnoseRagEvidence(
            content=chunk["content"],
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
    threshold = get_settings().cv_confidence_threshold
    if cv_result.confidence < threshold:
        expected_path = "low_confidence_review"
    elif len(structured_context.rules) == 1:
        expected_path = "treatment"
    else:
        expected_path = "review_required"
    if structured_context.path != expected_path:
        raise ValueError(
            f"Recommendation path must be {expected_path} for the current safety state"
        )
    if structured_context.path == "treatment":
        if len(structured_context.rules) != 1 or selected_rule_id is None:
            raise ValueError("Treatment recommendation must select its only rule_id")
    elif selected_rule_id is not None:
        raise ValueError("Review recommendation cannot select a rule_id")

    return DiagnoseResponse(
        request_id=payload.request_id,
        cv_result=cv_result,
        rag_evidence=rag_evidence,
        structured_context=structured_context,
        recommendation=DiagnoseRecommendation(
            selected_rule_id=selected_rule_id,
            summary=recommendation.get("summary", ""),
            requires_engineer_approval=True,
        ),
    )
