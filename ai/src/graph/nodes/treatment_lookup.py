from typing import Any

from langgraph.runtime import Runtime

from src.graph.context import WorkflowRuntime
from src.graph.state import FarmState
from src.services.structured_knowledge_service import lookup_structured_knowledge


def treatment_lookup(
    state: FarmState, runtime: Runtime[WorkflowRuntime]
) -> dict[str, Any]:
    code = (state.get("cv_result") or {}).get("disease_code")
    if not code:
        raise ValueError("cv_result.disease_code is required")

    tool = runtime.context.treatment_tool
    if tool is not None:
        return {"treatment_rule": tool(code)}
    if runtime.context.db is not None:
        return {"treatment_rule": lookup_structured_knowledge(runtime.context.db, code)}
    if not state.get("treatment_rule"):
        raise ValueError("DB, treatment_tool or treatment_rule is required")
    return {}
