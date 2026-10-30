# SPDX-License-Identifier: AGPL-3.0-only
"""Placeholder implementation for resolve_request node.
It simply returns the received state unchanged. This allows the LangGraph
pipeline to run in minimal mode before the full logic is added.
"""

from typing import Any

from app.graph.state import FarmState  # type: ignore


def resolve_request(state: FarmState) -> dict[str, Any]:
    """Pass‑through node – no processing.
    Args:
        state: Current workflow state.
    Returns:
        The unchanged state.
    """
    request_id = str(state.get("request_id", "")).strip()
    if not request_id:
        raise ValueError("request_id is required")
    if len(request_id) > 100:
        raise ValueError("request_id is too long")
    image = state.get("image_ref")
    if not isinstance(image, bytes) or not image:
        raise ValueError("image_ref must contain image bytes")
    crop_season_id = state.get("crop_season_id")
    if not isinstance(crop_season_id, int) or crop_season_id <= 0:
        raise ValueError("crop_season_id must be a positive integer")
    if not state.get("field_context"):
        raise ValueError("field_context is required")
    return {
        "request_id": request_id,
        "errors": list(state.get("errors", [])),
        "timings_ms": dict(state.get("timings_ms", {})),
    }
