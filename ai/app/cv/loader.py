import torch
from huggingface_hub import hf_hub_download

from app.core.config import Settings, get_settings
from .classifier import EfficientNetB4Classifier


def download_model(settings: Settings | None = None) -> EfficientNetB4Classifier:
    """Download the model weights (if needed) and return a ready‑to‑use EfficientNetB4 model.

    The function respects ``settings.cv_device`` which can be ``"auto"`` (default), ``"cuda"`` or ``"cpu"``.
    The chosen torch device is stored on the ``Settings`` instance as ``_chosen_device`` for later reference.
    """
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

    model = EfficientNetB4Classifier(num_classes=settings.cv_num_classes, pretrained=False)
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()

    # Determine device
    device_setting = settings.cv_device
    if device_setting == "auto":
        device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    else:
        device = torch.device(device_setting)

    model.to(device)
    # Store chosen device for later use (e.g., in health endpoint)
    setattr(settings, "_chosen_device", device)
    return model
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
