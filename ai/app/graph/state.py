# SPDX-License-Identifier: Apache-2.0
"""TypedDict definition for LangGraph workflow state.
Only the fields required for the minimal flow are defined. Additional fields
can be added later without breaking existing code.
"""

from typing import TypedDict, Optional, List, Dict, Any


class FarmState(TypedDict, total=False):
    # Input fields
    request_id: str
    actor_id: str
    actor_role: str
    parcel_code: str
    crop_season_id: str
    image_ref: str
    # CV output
    cv_result: Optional[Dict[str, Any]]
    # Data outputs
    field_context: Optional[Dict[str, Any]]
    treatment_rule: Optional[Dict[str, Any]]
    rag_context: Optional[List[Dict[str, Any]]]
    # LLM output
    recommendation: Optional[str]
    # Approval
    approval_status: Optional[str]
    application_status: Optional[str]
    # Final response
    response: Optional[Dict[str, Any]]
    # Internal helpers
    db: Any  # SQLAlchemy Session, injected by FastAPI endpoint
    rag_question: Optional[str]
    rag_top_k: Optional[int]
    approval_reason: Optional[str]
    selected_rule_id: Optional[str]
    approved_dose: Optional[float]
    applied_at: Any
    errors: List[str]
    vision_tool: Any
    approval_reason: Optional[str]
    selected_rule_id: Optional[str]
    approved_dose: Optional[float]
    applied_at: Any
    errors: List[str]
