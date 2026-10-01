# SPDX-License-Identifier: Apache-2.0
import hashlib
from pathlib import Path

import torch

from app.core.config import Settings, get_settings
from app.cv.contract import class_contract
from app.cv.model_profile import MODEL_WEIGHTS_SHA256


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _model_names(model) -> list[str]:
    names = getattr(model, "names", None)
    if isinstance(names, dict):
        try:
            return [names[index] for index in range(len(names))]
        except KeyError as exc:
            raise ValueError("YOLO class IDs are not contiguous from zero") from exc
    if isinstance(names, (list, tuple)):
        return list(names)
    raise ValueError("YOLO checkpoint does not expose class names")


def validate_model_contract(model, contract: dict) -> None:
    task = getattr(model, "task", None)
    if task != "detect":
        raise ValueError(f"YOLO checkpoint task must be 'detect', got {task!r}")
    expected_names = [item["model_label"] for item in contract["classes"]]
    actual_names = _model_names(model)
    if actual_names != expected_names:
        raise ValueError(
            "YOLO checkpoint class order does not match class_contract.json: "
            f"expected {expected_names}, got {actual_names}"
        )


def _resolve_device(configured: str) -> str:
    if configured != "auto":
        return configured
    return "cuda" if torch.cuda.is_available() else "cpu"


def load_model(settings: Settings | None = None):
    settings = settings or get_settings()
    path = settings.cv_weights_path
    if not path.is_file():
        raise ValueError(f"CV checkpoint does not exist or is not a file: {path}")
    if not path.stat().st_size:
        raise ValueError(f"CV checkpoint is empty: {path}")
    if _sha256(path) != MODEL_WEIGHTS_SHA256:
        raise ValueError("CV checkpoint SHA-256 does not match model profile")

    # Import only after local file validation. No checkpoint is downloaded implicitly.
    from ultralytics import YOLO

    model = YOLO(str(path), task="detect")
    contract = class_contract(settings.cv_class_contract_path)
    validate_model_contract(model, contract)
    device = _resolve_device(settings.cv_device)
    model.to(device)
    model._dxfarm_device = device
    return model
