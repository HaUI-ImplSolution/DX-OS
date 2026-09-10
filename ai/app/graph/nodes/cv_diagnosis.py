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
    tool = state.get("vision_tool")
    if tool is None: raise ValueError("vision_tool is required in FarmState")
    image = state.get("image_ref")
    if isinstance(image, str):
        with open(image, "rb") as fh: image = fh.read()
    state["cv_result"] = tool(image) if callable(tool) else tool.predict(image)
    return state
