from io import BytesIO

import torch
from PIL import Image

from app.core.config import Settings, get_settings
from app.cv.class_names import CLASS_NAMES, get_disease_name
from app.cv.contract import canonical_code


class PlantDiseaseService:
    def __init__(self, model, settings: Settings | None = None):
        self.model = model
        self.model.eval()
        self.settings = settings or get_settings()
        # Retrieve device from settings (set in loader)
        self.device = getattr(self.settings, "_chosen_device", torch.device("cpu"))
        self.model.to(self.device)

    def predict(self, image_bytes: bytes) -> dict:
        try:
            image = Image.open(BytesIO(image_bytes)).convert("RGB")
        except (OSError, ValueError) as exc:
            raise ValueError("Invalid image data") from exc

        # Lazy import for preprocessing
        from app.cv.preprocessor import prepare_input_image

        image = prepare_input_image(image, img_size=self.settings.cv_image_size)
        image_tensor = self._preprocess(image).unsqueeze(0).to(self.device)

        import time

        start = time.time()
        with torch.no_grad():
            output = self.model(image_tensor)
        inference_time_ms = (time.time() - start) * 1000

        if output.shape != (1, len(CLASS_NAMES)) or not torch.isfinite(output).all():
            raise ValueError("Invalid model output")
        probabilities = torch.softmax(output, dim=1)
        predicted_class = probabilities.argmax(dim=1).item()
        confidence = probabilities[0, predicted_class].item()

        disease_code = canonical_code(CLASS_NAMES[predicted_class])
        disease_name = get_disease_name(disease_code)

        return {
            "class_id": predicted_class,
            "class_name": CLASS_NAMES[predicted_class],
            "disease_code": disease_code,
            "disease_name": disease_name,
            "confidence": round(confidence, 3),
            "model_name": self.settings.cv_model_name,
            "model_version": self.settings.cv_model_version,
            "inference_time_ms": inference_time_ms,
        }

    @staticmethod
    def _preprocess(image: Image.Image) -> torch.Tensor:
        from torchvision import transforms

        transform = transforms.Compose(
            [
                transforms.ToTensor(),
                transforms.Normalize(
                    mean=[0.485, 0.456, 0.406],
                    std=[0.229, 0.224, 0.225],
                ),
            ]
        )
        return transform(image)
