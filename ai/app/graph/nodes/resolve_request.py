# SPDX-License-Identifier: Apache-2.0
"""Placeholder implementation for resolve_request node.
It simply returns the received state unchanged. This allows the LangGraph
pipeline to run in minimal mode before the full logic is added.
"""

from typing import Any, Dict
from app.graph.state import FarmState  # type: ignore

def resolve_request(state: FarmState) -> Dict[str, Any]:
    """Pass‑through node – no processing.
    Args:
        state: Current workflow state.
    Returns:
        The unchanged state.
    """
    if not state.get("request_id"): raise ValueError("request_id is required")
    if not state.get("image_ref"): raise ValueError("image_ref is required")
    if not state.get("parcel_code") and not state.get("crop_season_id"): raise ValueError("parcel_code or crop_season_id is required")
    state.setdefault("approval_status", "PENDING")
    return state
