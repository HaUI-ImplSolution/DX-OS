# SPDX-License-Identifier: AGPL-3.0-only
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
    crop_season_id: int
    image_ref: Any

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

    # Final response
    response: dict[str, Any] | None

    # Retrieval controls and diagnostic metadata
    rag_question: str | None
    rag_top_k: int | None
    errors: list[str]
    timings_ms: dict[str, float]
