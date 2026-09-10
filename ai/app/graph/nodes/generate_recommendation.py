# SPDX-License-Identifier: Apache-2.0
"""Generate recommendation node.
Creates a simple textual recommendation using CV result, treatment rule, and optional RAG context.
This placeholder avoids heavy LLM calls – it builds a deterministic string for minimal MVP.
"""

from typing import Any, Dict
from app.graph.state import FarmState  # type: ignore


def generate_recommendation(state: FarmState) -> Dict[str, Any]:
    """Create a deterministic recommendation.
    Args:
        state: Current workflow state containing `cv_result`, `treatment_rule`, and `rag_context`.
    Returns:
        State with a new key `recommendation` containing a short recommendation string.
    """
    cv = state.get("cv_result", {}) or {}
    treatment = state.get("treatment_rule", {}) or {}
    rag = state.get("rag_context", []) or []

    disease_name = cv.get("disease_name", "unknown disease")
    pesticide = treatment.get("pesticide_name", "unknown pesticide")
    dose = treatment.get("dose", "N/A")
    unit = treatment.get("unit", "")

    rec = f"For {disease_name}, apply {pesticide} at {dose}{unit}."
    if rag:
        rec += f" Retrieved {len(rag)} knowledge snippet(s) for reference."
    state["recommendation"] = rec
    return state
