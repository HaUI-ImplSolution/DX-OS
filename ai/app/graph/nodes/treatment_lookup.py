from typing import Any, Dict
from app.graph.state import FarmState
from app.services.structured_knowledge_service import lookup_structured_knowledge

def treatment_lookup(state: FarmState) -> Dict[str, Any]:
    code = (state.get("cv_result") or {}).get("disease_code")
    if not code: raise ValueError("cv_result.disease_code is required")
    state["treatment_rule"] = lookup_structured_knowledge(state["db"], code) if state.get("db") else {"path": "review_required", "disease_code": code, "rules": []}
    return state
