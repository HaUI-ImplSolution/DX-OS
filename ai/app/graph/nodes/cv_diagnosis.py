# SPDX-License-Identifier: Apache-2.0
import math
import time
import uuid
from typing import Any

from app.core.config import get_settings
from app.cv.contract import class_contract
from app.graph.state import FarmState  # type: ignore
from app.services.diagnosis_service import persist_prediction


def cv_diagnosis(state: FarmState) -> dict[str, Any]:
    """Execute CV diagnosis node using vision_tool or existing cv_result."""
    started = time.perf_counter()
    cv_res = state.get("cv_result")

    if not cv_res:
        tool = state.get("vision_tool")
        if tool is None:
            raise ValueError("vision_tool or cv_result is required in FarmState")

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
    valid_codes = {item["disease_code"] for item in class_contract()["classes"]}
    if disease_code not in valid_codes:
        raise ValueError("CV diagnosis failed: unknown canonical disease_code")
    confidence = cv_res.get("confidence")
    if not isinstance(confidence, (float, int)) or not math.isfinite(confidence):
        raise ValueError("CV diagnosis failed: invalid confidence")
    if not 0 <= confidence <= 1:
        raise ValueError("CV diagnosis failed: confidence outside [0,1]")
    if not cv_res.get("model_name") or not cv_res.get("model_version"):
        raise ValueError("CV diagnosis failed: model metadata is required")

    state["cv_result"] = cv_res
    state["disease_code"] = disease_code
    state["confidence"] = float(confidence)
    state["low_confidence"] = confidence < get_settings().cv_confidence_threshold
    state.setdefault("timings_ms", {})["cv_inference"] = round(
        float(cv_res.get("inference_time_ms", 0.0)), 2
    )

    db = state.get("db")
    report_id = state.get("disease_report_id")
    persistence_started = time.perf_counter()
    if db is not None and report_id:
        persisted = persist_prediction(db, uuid.UUID(str(report_id)), cv_res)
        state["disease_report_id"] = persisted["report_id"]
    state["timings_ms"]["cv_persistence"] = round(
        (time.perf_counter() - persistence_started) * 1000, 2
    )
    state["timings_ms"]["cv_node_total"] = round(
        (time.perf_counter() - started) * 1000, 2
    )
    return state
