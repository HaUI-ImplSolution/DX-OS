# SPDX-License-Identifier: Apache-2.0
import torch
from huggingface_hub import hf_hub_download

from app.core.config import Settings, get_settings
from app.cv.class_names import CLASS_NAMES
from app.cv.classifier import EfficientNetB4Classifier


def download_model(settings: Settings | None = None) -> EfficientNetB4Classifier:
    settings = settings or get_settings()
    settings.weights_dir.mkdir(parents=True, exist_ok=True)
    if not settings.weights_path.exists():
        hf_hub_download(
            repo_id=settings.hf_repo_id,
            filename=settings.weights_filename,
            local_dir=settings.weights_dir,
        )
    checkpoint = torch.load(
        settings.weights_path, map_location="cpu", weights_only=True
    )
    config = checkpoint.get("config", {})
    if (
        config.get("data", {}).get("img_size", settings.cv_image_size)
        != settings.cv_image_size
    ):
        raise ValueError("CV image size does not match checkpoint")
    if (
        config.get("model", {}).get("num_classes", settings.cv_num_classes)
        != settings.cv_num_classes
    ):
        raise ValueError("CV class count does not match checkpoint")
    if "class_to_idx" in checkpoint:
        expected = {label: index for index, label in CLASS_NAMES.items()}
        if checkpoint["class_to_idx"] != expected:
            raise ValueError("Checkpoint class order does not match CV contract")
    model = EfficientNetB4Classifier(
        num_classes=settings.cv_num_classes, pretrained=False
    )
    model.load_state_dict(checkpoint["model_state_dict"])
    device = torch.device(
        "cuda"
        if settings.cv_device == "auto" and torch.cuda.is_available()
        else "cpu"
        if settings.cv_device == "auto"
        else settings.cv_device
    )
    model.to(device).eval()
    settings._chosen_device = device
    return model
