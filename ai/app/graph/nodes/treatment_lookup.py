from typing import Any

from langgraph.runtime import Runtime

from app.graph.context import WorkflowRuntime
from app.graph.state import FarmState


def treatment_lookup(
    state: FarmState, runtime: Runtime[WorkflowRuntime]
) -> dict[str, Any]:
    code = (state.get("cv_result") or {}).get("disease_code")
    if not code:
        raise ValueError("cv_result.disease_code is required")

    tool = runtime.context.treatment_tool
    if tool is not None:
        facts = dict(tool(code) or {})
        rules = list(facts.get("rules") or [])
        if state.get("low_confidence"):
            path = "low_confidence_review"
        elif len(rules) == 1:
            path = "treatment"
        else:
            path = "review_required"
        return {"treatment_rule": {**facts, "path": path, "rules": rules}}
    if state.get("treatment_rule"):
        return {}
    raise ValueError("treatment_tool or treatment_rule is required")
