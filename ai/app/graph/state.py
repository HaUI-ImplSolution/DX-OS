# SPDX-License-Identifier: Apache-2.0
"""TypedDict definition for LangGraph workflow state.
Only the fields required for the minimal flow are defined. Additional fields
can be added later without breaking existing code.
"""

from typing import Any, TypedDict


class FarmState(TypedDict, total=False):
    # Input fields
    request_id: str
    actor_id: str
    actor_role: str
    parcel_code: str
    crop_season_id: str
    image_ref: Any
    disease_report_id: str

    # CV output
    cv_result: dict[str, Any] | None
    disease_code: str
    confidence: float
    low_confidence: bool

    # Data outputs
    field_context: dict[str, Any] | None
    treatment_rule: dict[str, Any] | None
    rag_context: list[dict[str, Any]] | None

    # Recommendation output
    recommendation: Any | None

    # Approval & Application status
    approval_status: str | None
    approval_reason: str | None
    selected_rule_id: str | None
    approved_dose: float | None
    application_status: str | None
    applied_at: Any

    # Final response
    response: dict[str, Any] | None

    # Internal helpers / context
    db: Any  # SQLAlchemy Session, injected by API / caller
    vision_tool: Any
    field_context_tool: Any
    treatment_tool: Any
    rag_tool: Any
    recommendation_writer: Any
    rag_question: str | None
    rag_top_k: int | None
    errors: list[str]
    timings_ms: dict[str, float]
