import uuid
from typing import Any

from app.graph.state import FarmState
from app.services.field_context_service import get_field_context


def load_field_context(state: FarmState) -> dict[str, Any]:
    tool = state.get("field_context_tool")
    if tool is not None:
        state["field_context"] = tool(
            state.get("parcel_code"), state.get("crop_season_id")
        )
    elif state.get("db") is not None:
        season_id = state.get("crop_season_id")
        state["field_context"] = get_field_context(
            state["db"],
            state.get("parcel_code"),
            uuid.UUID(str(season_id)) if season_id else None,
        )
    else:
        if not state.get("field_context"):
            raise ValueError("DB or field_context is required")
    return state
