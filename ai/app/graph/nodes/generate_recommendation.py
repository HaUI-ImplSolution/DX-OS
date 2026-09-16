# SPDX-License-Identifier: Apache-2.0
"""Build a structured recommendation and an optional grounded explanation."""

import time
from typing import Any

from langgraph.runtime import Runtime

from app.graph.context import WorkflowRuntime
from app.graph.state import FarmState  # type: ignore


def generate_recommendation(
    state: FarmState, runtime: Runtime[WorkflowRuntime]
) -> dict[str, Any]:
    """Generate structured recommendation while strictly adhering to safety guardrails.

    Guardrails:
    - LLM / Generator MUST NOT invent pesticides, active ingredients, doses, or PHI.
    - If path == 'treatment', use strictly provided TreatmentRule(s).
    - If path == 'policy', do NOT propose chemical products.
    - Include RAG source citations when available.
    """
    cv = state.get("cv_result", {}) or {}
    treatment_lookup = state.get("treatment_rule", {}) or {}
    rag_context = state.get("rag_context", []) or []

    disease_code = cv.get("disease_code", "UNKNOWN")
    disease_name = cv.get("disease_name", cv.get("disease_code", "không xác định"))
    confidence = cv.get("confidence", 0.0)
    path = treatment_lookup.get("path", "review_required")

    source_ids = []
    for chunk in rag_context:
        for s_id in chunk.get("source_ids", []):
            if s_id not in source_ids:
                source_ids.append(s_id)

    actions = []
    summary_parts = [
        f"Chẩn đoán nhận diện: {disease_name} ({disease_code}) với độ tin cậy {confidence:.1%}."
    ]

    if path == "treatment":
        rules = treatment_lookup.get("rules", [])
        summary_parts.append(f"Tìm thấy {len(rules)} quy trình điều trị được duyệt.")
        for r in rules:
            actions.append(
                {
                    "rule_id": r.get("rule_id"),
                    "pesticide": r.get("pesticide"),
                    "dose_amount": r.get("dose_amount"),
                    "dose_unit": r.get("dose_unit"),
                    "phi_days": r.get("phi_days"),
                    "version": r.get("version"),
                }
            )
        if actions:
            selected = actions[0]
            summary_parts.append(
                f"Khuyến nghị dùng {selected['pesticide']} với liều "
                f"{selected['dose_amount']} {selected['dose_unit']}; PHI {selected['phi_days']} ngày."
            )
    elif path == "policy":
        policy = treatment_lookup.get("policy") or {}
        status = policy.get("management_status", "NO_CHEMICAL_CONTROL")
        notes = policy.get("notes", "Không áp dụng hóa chất cho bệnh này.")
        summary_parts.append(f"Chính sách quản lý: {status}. {notes}")
    else:
        summary_parts.append(
            "Chưa có quy trình hóa chất hoặc chính sách cố định cho bệnh này. Cần kỹ sư nông nghiệp đánh giá chuyên sâu."
        )

    if rag_context:
        summary_parts.append(
            f"Tham khảo {len(rag_context)} đoạn tài liệu kiến thức (Nguồn: {', '.join(source_ids) if source_ids else 'hệ thống RAG'})."
        )

    recent_treatments = (state.get("field_context") or {}).get("recent_treatments", [])
    if recent_treatments:
        summary_parts.append(
            f"Đã đối chiếu {len(recent_treatments)} lần xử lý gần nhất."
        )

    evidence = "\n\n".join(chunk.get("content", "") for chunk in rag_context)
    explanation = evidence
    generation_mode = "evidence_fallback" if evidence else "no_evidence"
    writer = runtime.context.recommendation_writer
    started = time.perf_counter()
    if writer is not None:
        explanation, generation_mode = writer.write(evidence)
    if explanation:
        summary_parts.append(f"Khuyến nghị từ tài liệu: {explanation}")
    timings = dict(state.get("timings_ms", {}))
    timings["llm_generation"] = round((time.perf_counter() - started) * 1000, 2)

    recommendation_payload = {
        "selected_rule_id": actions[0]["rule_id"] if actions else None,
        "path": path,
        "disease_code": disease_code,
        "disease_name": disease_name,
        "confidence": confidence,
        "summary": " ".join(summary_parts),
        "explanation": explanation,
        "generation_mode": generation_mode,
        "actions": actions,
        "policy": treatment_lookup.get("policy"),
        "rag_sources": source_ids,
        "requires_engineer_approval": True,
        "low_confidence": bool(state.get("low_confidence")),
    }

    return {"recommendation": recommendation_payload, "timings_ms": timings}
