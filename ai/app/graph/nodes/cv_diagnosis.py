# SPDX-License-Identifier: Apache-2.0
"""Placeholder implementation for cv_diagnosis node.
It simply returns the state unchanged. In a full implementation it would call the
vision_tool and store the CV result.
"""

from typing import Any, Dict
from app.graph.state import FarmState  # type: ignore

def cv_diagnosis(state: FarmState) -> Dict[str, Any]:
    """Pass‑through node – placeholder for CV diagnosis.
    Args:
        state: Current workflow state.
    Returns:
        Unmodified state.
    """
    return state

