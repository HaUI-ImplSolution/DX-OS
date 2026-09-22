from typing import Any

from langgraph.runtime import Runtime

from app.graph.context import WorkflowRuntime
from app.graph.state import FarmState


def load_field_context(
    state: FarmState, runtime: Runtime[WorkflowRuntime]
) -> dict[str, Any]:
    if state.get("field_context"):
        return {}
    tool = runtime.context.field_context_tool
    if tool is not None:
        return {
            "field_context": tool(state.get("parcel_code"), state.get("crop_season_id"))
        }
    raise ValueError("field_context or field_context_tool is required")
