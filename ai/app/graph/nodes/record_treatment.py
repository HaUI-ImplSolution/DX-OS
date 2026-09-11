from typing import Any

from app.graph.state import FarmState


def record_treatment(state: FarmState) -> dict[str, Any]:
    """Node executed when engineer approves recommendation.

    Hard Rule: APPROVED != APPLIED.
    Approval marks recommendation as approved and prepares for application confirmation.
    No PHI lock is created until actual application (APPLIED state in Stage 9).
    """
    state["approval_status"] = "APPROVED"
    state["application_status"] = "WAITING_APPLIED"

    rec = state.get("recommendation", {})
    selected_rule_id = state.get("selected_rule_id")

    state["response"] = {
        "status": "APPROVED",
        "message": "Recommendation approved by engineer. Waiting for field application (APPLIED).",
        "selected_rule_id": selected_rule_id,
        "approved_dose": state.get("approved_dose"),
        "recommendation": rec,
    }
    return state
