# SPDX-License-Identifier: Apache-2.0
"""Placeholder implementation for cv_diagnosis node.
It simply returns the state unchanged. In a full implementation it would call the
vision_tool and store the CV result.
"""

from typing import Any, Dict
from app.graph.state import FarmState  # type: ignore

def cv_diagnosis(state: FarmState) -> Dict[str, Any]:
    """Execute CV diagnosis node using vision_tool or existing cv_result."""
    cv_res = state.get("cv_result")

    if not cv_res:
        tool = state.get("vision_tool")
        if tool is None:
            raise ValueError("vision_tool or cv_result is required in FarmState")

        image = state.get("image_ref")
        if isinstance(image, str):
            with open(image, "rb") as fh:
                image_bytes = fh.read()
        elif isinstance(image, bytes):
            image_bytes = image
        else:
            image_bytes = image

        if callable(tool):
            raw_res = tool(image_bytes)
        elif hasattr(tool, "predict"):
            raw_res = tool.predict(image_bytes)
        else:
            raise ValueError("Invalid vision_tool interface")

        if isinstance(raw_res, dict):
            cv_res = raw_res
        elif hasattr(raw_res, "model_dump"):
            cv_res = raw_res.model_dump()
        else:
            cv_res = dict(raw_res)

    # Ensure required keys in cv_result
    disease_code = cv_res.get("disease_code")
    if not disease_code:
        raise ValueError("CV diagnosis failed: missing disease_code in output")

    state["cv_result"] = cv_res
    return state

