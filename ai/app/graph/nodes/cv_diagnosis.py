# SPDX-License-Identifier: Apache-2.0
import math
import time
from typing import Any

from langgraph.runtime import Runtime

from app.core.config import get_settings
from app.cv.contract import class_contract
from app.graph.context import WorkflowRuntime
from app.graph.state import FarmState  # type: ignore


def cv_diagnosis(state: FarmState, runtime: Runtime[WorkflowRuntime]) -> dict[str, Any]:
    """Execute CV diagnosis node using vision_tool or existing cv_result."""
    started = time.perf_counter()
    cv_res = state.get("cv_result")

    if not cv_res:
        tool = runtime.context.vision_tool
        if tool is None:
            raise ValueError("vision_tool or cv_result is required")

        image_bytes = state.get("image_ref")

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

    disease_code = cv_res.get("disease_code")
    if not disease_code:
        raise ValueError("CV diagnosis failed: missing disease_code in output")
    contract = class_contract()
    valid_codes = {item["disease_code"] for item in contract["classes"]}
    valid_codes.add(contract["fallback"]["disease_code"])
    if disease_code not in valid_codes:
        raise ValueError("CV diagnosis failed: unknown canonical disease_code")
    confidence = cv_res.get("confidence")
    if not isinstance(confidence, (float, int)) or not math.isfinite(confidence):
        raise ValueError("CV diagnosis failed: invalid confidence")
    if not 0 <= confidence <= 1:
        raise ValueError("CV diagnosis failed: confidence outside [0,1]")
    if not cv_res.get("model_name") or not cv_res.get("model_version"):
        raise ValueError("CV diagnosis failed: model metadata is required")

    timings = dict(state.get("timings_ms", {}))
    timings["cv_inference"] = round(float(cv_res.get("inference_time_ms", 0.0)), 2)

    updates: dict[str, Any] = {
        "cv_result": cv_res,
        "disease_code": disease_code,
        "confidence": float(confidence),
        "low_confidence": confidence < get_settings().cv_confidence_threshold,
    }
    timings["cv_node_total"] = round((time.perf_counter() - started) * 1000, 2)
    updates["timings_ms"] = timings
    return updates
