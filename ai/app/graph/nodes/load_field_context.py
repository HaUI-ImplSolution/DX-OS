import uuid
from typing import Any

from langgraph.runtime import Runtime

from app.graph.context import WorkflowRuntime
from app.graph.state import FarmState
from app.services.field_context_service import get_field_context


def load_field_context(
    state: FarmState, runtime: Runtime[WorkflowRuntime]
) -> dict[str, Any]:
    tool = runtime.context.field_context_tool
    if tool is not None:
        return {"field_context": tool(
            state.get("parcel_code"), state.get("crop_season_id")
        )}
    if runtime.context.db is not None:
        season_id = state.get("crop_season_id")
        return {"field_context": get_field_context(
            runtime.context.db,
            state.get("parcel_code"),
            uuid.UUID(str(season_id)) if season_id else None,
        )}
    if not state.get("field_context"):
        raise ValueError("DB or field_context is required")
    return {}
