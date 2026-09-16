# SPDX-License-Identifier: Apache-2.0
"""Stateless inference service coordinating CV diagnosis, RAG vector retrieval, and Backend structured facts."""

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.clients.backend_client import BackendClient
from app.llm.recommendation_writer import GroundedRecommendationWriter
from app.rag.retriever import RAGRetriever
from app.services.plant_disease_service import PlantDiseaseService


def run_stateless_diagnose(
    *,
    image_bytes: bytes,
    request_id: str | None = None,
    crop_season_id: str | None = None,
    parcel_code: str | None = None,
    rag_question: str | None = None,
    field_context: dict[str, Any] | None = None,
    predictor: PlantDiseaseService,
    db: Session,
    backend_client: BackendClient | None = None,
) -> dict[str, Any]:
    """Execute stateless AI inference: CV + RAG + Backend Structured Facts -> Recommendation."""
    req_id = request_id or f"req_{uuid.uuid4().hex[:12]}"
    client = backend_client or BackendClient()

    # 1. Computer Vision Diagnosis
    cv_result = predictor.predict(image_bytes)
    disease_code = cv_result["disease_code"]
    disease_name = cv_result.get("disease_name", disease_code)

    # 2. RAG Vector Retrieval from AI Vector DB
    query = rag_question.strip() if rag_question and rag_question.strip() else f"Triệu chứng và phòng trừ bệnh {disease_name}"
    rag_chunks = RAGRetriever().retrieve(
        db=db,
        query=query,
        disease_code=disease_code,
        top_k=3,
    )
    rag_evidence = [
        {
            "content": chunk["content"],
            "source_ids": chunk.get("source_ids", []),
            "similarity": round(chunk.get("similarity", 0.0), 4),
        }
        for chunk in rag_chunks
    ]

    # 3. Fetch Structured Facts from Backend Internal REST API (Fail-fast as Backend is single source of business truth)
    structured_context = client.get_structured_facts(
        disease_code=disease_code,
        crop_season_id=crop_season_id,
        request_id=req_id,
    )

    # 4. Synthesize Recommendation
    writer = GroundedRecommendationWriter()
    evidence_text = "\n\n".join(item["content"] for item in rag_evidence)
    explanation, generation_mode = writer.write(evidence_text)

    path = structured_context.get("path", "review_required")
    rules = structured_context.get("rules", [])
    policy = structured_context.get("policy")

    actions = []
    if path == "treatment" and rules:
        for r in rules:
            actions.append(
                {
                    "rule_id": r.get("rule_id") or r.get("id"),
                    "pesticide": r.get("pesticide"),
                    "active_ingredient": r.get("active_ingredient"),
                    "dose_min": r.get("dose_min"),
                    "dose_max": r.get("dose_max"),
                    "dose_unit": r.get("dose_unit"),
                    "phi_days": r.get("phi_days"),
                    "source": r.get("source"),
                    "version": r.get("version"),
                }
            )

    selected_rule_id = actions[0]["rule_id"] if actions else None

    summary_parts = [
        f"Chẩn đoán nhận diện: {disease_name} ({disease_code}) với độ tin cậy {cv_result['confidence']:.1%}."
    ]
    if path == "treatment":
        summary_parts.append(f"Tìm thấy {len(actions)} quy trình điều trị được Backend duyệt.")
    elif path == "policy":
        summary_parts.append("Áp dụng chính sách quản lý sinh học/canh tác, không đề xuất hóa chất.")
    else:
        summary_parts.append("Cần kỹ sư nông nghiệp đánh giá chuyên sâu.")

    recommendation = {
        "selected_rule_id": str(selected_rule_id) if selected_rule_id else None,
        "summary": " ".join(summary_parts),
        "explanation": explanation,
        "generation_mode": generation_mode,
        "path": path,
        "actions": actions,
        "policy": policy,
        "requires_engineer_approval": True,
    }

    return {
        "request_id": req_id,
        "cv_result": cv_result,
        "rag_evidence": rag_evidence,
        "structured_context": structured_context,
        "recommendation": recommendation,
    }
