from typing import Any, Dict
from app.graph.state import FarmState
from app.services.field_context_service import get_field_context

def load_field_context(state: FarmState) -> Dict[str, Any]:
    if state.get("db") is not None and state.get("parcel_code"):
        state["field_context"] = get_field_context(state["db"], state["parcel_code"])
    else:
        state["field_context"] = state.get("field_context") or {}
    return state
