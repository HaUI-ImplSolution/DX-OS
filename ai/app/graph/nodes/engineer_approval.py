from typing import Any

from app.graph.state import FarmState


def engineer_approval(state: FarmState) -> dict[str, Any]:
    status = state.get("approval_status", "PENDING")
    if status not in {"PENDING", "APPROVED", "REJECTED"}:
        raise ValueError("approval_status must be PENDING, APPROVED, or REJECTED")
    if status == "REJECTED" and not state.get("approval_reason"):
        raise ValueError("approval_reason is required for REJECTED")
    state["approval_status"] = status
    return state
