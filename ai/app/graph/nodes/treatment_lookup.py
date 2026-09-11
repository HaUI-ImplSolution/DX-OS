from typing import Any

from app.graph.state import FarmState
from app.services.structured_knowledge_service import lookup_structured_knowledge


def treatment_lookup(state: FarmState) -> dict[str, Any]:
    code = (state.get("cv_result") or {}).get("disease_code")
    if not code:
        raise ValueError("cv_result.disease_code is required")

    tool = state.get("treatment_tool")
    if tool is not None:
        state["treatment_rule"] = tool(code)
    elif state.get("db") is not None:
        state["treatment_rule"] = lookup_structured_knowledge(state["db"], code)
    else:
        if not state.get("treatment_rule"):
            raise ValueError("DB, treatment_tool or treatment_rule is required")
    return state
