# SPDX-License-Identifier: Apache-2.0
"""Placeholder implementation for resolve_request node.
It simply returns the received state unchanged. This allows the LangGraph
pipeline to run in minimal mode before the full logic is added.
"""

import uuid
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
    if not state.get("parcel_code") and not state.get("crop_season_id"):
        raise ValueError("parcel_code or crop_season_id is required")
    if state.get("crop_season_id"):
        try:
            uuid.UUID(str(state["crop_season_id"]))
        except ValueError as exc:
            raise ValueError("crop_season_id must be a UUID") from exc
    return {
        "request_id": request_id,
        "errors": list(state.get("errors", [])),
        "timings_ms": dict(state.get("timings_ms", {})),
    }
