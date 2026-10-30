# SPDX-License-Identifier: AGPL-3.0-only
"""Build the structured recommendation from the CV result and treatment facts."""

from typing import Any

from langgraph.runtime import Runtime

from app.graph.context import WorkflowRuntime
from app.graph.state import FarmState  # type: ignore


def generate_recommendation(
    state: FarmState, runtime: Runtime[WorkflowRuntime]
) -> dict[str, Any]:
    """Generate structured recommendation while strictly adhering to safety guardrails.

    Guardrails:
    - Pesticides, active ingredients, doses and PHI come only from the provided TreatmentRule(s).
    - If path == 'policy', do NOT propose chemical products.
    - RAG passages are returned separately as evidence and never mixed into the actions.
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
    if path == "treatment":
        for r in treatment_lookup.get("rules", []):
            actions.append(
                {
                    "rule_id": r.get("rule_id"),
                    "pesticide": r.get("pesticide"),
                    "dose_min": r.get("dose_min"),
                    "dose_max": r.get("dose_max"),
                    "dose_unit": r.get("dose_unit"),
                    "phi_days": r.get("phi_days"),
                    "version": r.get("version"),
                }
            )

    recommendation_payload = {
        "selected_rule_id": actions[0]["rule_id"] if actions else None,
        "path": path,
        "disease_code": disease_code,
        "disease_name": disease_name,
        "confidence": confidence,
        "actions": actions,
        "policy": treatment_lookup.get("policy"),
        "rag_sources": source_ids,
        "requires_engineer_approval": True,
        "low_confidence": bool(state.get("low_confidence")),
    }

    return {"recommendation": recommendation_payload}
