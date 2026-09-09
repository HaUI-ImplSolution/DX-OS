import torch
from huggingface_hub import hf_hub_download
from pathlib import Path

from .classifier import EfficientNetB4Classifier

# Anchor: loader.py nằm ở app/cv/loader.py
# parents[0] = app/cv/
# parents[1] = app/
# parents[2] = ai/   <-- project root của AI service
# Weights luôn được lưu tại ai/weights/ bất kể file được gọi từ đâu
BASE_DIR = Path(__file__).resolve().parents[2]

MODEL_DIR = BASE_DIR / "weights"
MODEL_PATH = MODEL_DIR / "best_model.pth"


def download_model():
    MODEL_DIR.mkdir(parents=True, exist_ok=True)

    if not MODEL_PATH.exists():
        print("Downloading model...")

        hf_hub_download(
            repo_id="Khawajaa/plant-disease-detector",
            filename="best_model.pth",
            local_dir=MODEL_DIR,
        )
    else:
        print("Model already exists.")

    ckpt = torch.load(MODEL_PATH, map_location="cpu")

    model = EfficientNetB4Classifier(
        num_classes=38,
        pretrained=False,
    )

    model.load_state_dict(ckpt["model_state_dict"])
    model.eval()

    return model
