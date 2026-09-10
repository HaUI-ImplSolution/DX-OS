from typing import Any, Dict
from app.graph.state import FarmState
from app.services.structured_knowledge_service import lookup_structured_knowledge

def treatment_lookup(state: FarmState) -> Dict[str, Any]:
    code = (state.get("cv_result") or {}).get("disease_code")
    if not code:
        raise ValueError("cv_result.disease_code is required")

    if state.get("db") is not None:
        state["treatment_rule"] = lookup_structured_knowledge(state["db"], code)
    else:
        # Retain pre-set treatment_rule if present, otherwise default to review_required
        if not state.get("treatment_rule"):
            state["treatment_rule"] = {
                "path": "review_required",
                "disease_code": code,
                "rules": [],
            }
    return state

