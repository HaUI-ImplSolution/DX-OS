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
    return state

