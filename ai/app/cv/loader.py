import torch
from huggingface_hub import hf_hub_download

from app.core.config import Settings, get_settings

from .classifier import EfficientNetB4Classifier


def download_model(
    settings: Settings | None = None,
) -> EfficientNetB4Classifier:
    settings = settings or get_settings()
    settings.weights_dir.mkdir(parents=True, exist_ok=True)

    if not settings.weights_path.exists():
        print("Downloading model...")
        hf_hub_download(
            repo_id=settings.hf_repo_id,
            filename=settings.weights_filename,
            local_dir=settings.weights_dir,
        )
    else:
        print("Model already exists.")

    checkpoint = torch.load(settings.weights_path, map_location="cpu")

    model = EfficientNetB4Classifier(
        num_classes=settings.cv_num_classes,
        pretrained=False,
    )
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()
    return model
