# SPDX-License-Identifier: AGPL-3.0-only
import hashlib
import re
from pathlib import Path

import torch

from app.core.config import Settings, get_settings
from app.core.torch_cpu import configure_torch_cpu
from app.cv.contract import class_contract
from app.cv.model_profile import MODEL_IMAGE_SIZE, MODEL_MEAN, MODEL_STD


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_model_contract(checkpoint: dict, contract: dict) -> None:
    names = [item["model_label"] for item in contract["classes"]]
    expected = {
        "architecture": "efficientnet_v2_m",
        "img_size": MODEL_IMAGE_SIZE,
        "class_names": names,
        "class_to_idx": dict(zip(names, range(len(names)))),
        "imagenet_mean": list(MODEL_MEAN),
        "imagenet_std": list(MODEL_STD),
    }
    if (
        len(names) != 6
        or not isinstance(checkpoint, dict)
        or any(checkpoint.get(key) != value for key, value in expected.items())
    ):
        raise ValueError(
            "CV checkpoint classes or preprocessing do not match the contract"
        )


def _resolve_device(configured: str) -> str:
    if configured != "auto":
        return configured
    return "cuda" if torch.cuda.is_available() else "cpu"


def load_model(settings: Settings | None = None):
    settings = settings or get_settings()
    expected_sha256 = (settings.cv_weights_sha256 or "").strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha256):
        raise ValueError(
            "CV_WEIGHTS_SHA256 must be configured as 64 hexadecimal characters"
        )

    path = settings.cv_weights_path
    if not path.is_file():
        raise ValueError(f"CV checkpoint does not exist or is not a file: {path}")
    if not path.stat().st_size:
        raise ValueError(f"CV checkpoint is empty: {path}")
    if _sha256(path) != expected_sha256:
        raise ValueError("CV checkpoint SHA-256 does not match CV_WEIGHTS_SHA256")

    # Construct the architecture without downloading pretrained weights.
    from torchvision.models import efficientnet_v2_m

    checkpoint = torch.load(path, map_location="cpu", weights_only=True)
    contract = class_contract(settings.cv_class_contract_path)
    validate_model_contract(checkpoint, contract)
    configure_torch_cpu()
    model = efficientnet_v2_m(weights=None, num_classes=6)
    model.load_state_dict(checkpoint["state_dict"], strict=True)
    device = _resolve_device(settings.cv_device)
    model.to(device).eval()
    model._device = device
    return model
