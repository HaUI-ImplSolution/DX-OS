from typing import Any, Dict
from app.graph.state import FarmState


def reject_case(state: FarmState) -> Dict[str, Any]:
    """Node executed when engineer rejects recommendation.
    
    Sets approval_status to REJECTED, application_status to CANCELLED,
    and requires an audit reason.
    """
    reason = state.get("approval_reason")
    if not reason:
        raise ValueError("approval_reason is required when rejecting a recommendation")

    state["approval_status"] = "REJECTED"
    state["application_status"] = "CANCELLED"

    state["response"] = {
        "status": "REJECTED",
        "message": f"Recommendation rejected by engineer: {reason}",
        "approval_reason": reason,
        "recommendation": state.get("recommendation"),
    }
    return state


