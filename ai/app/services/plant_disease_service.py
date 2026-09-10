from io import BytesIO

import torch
from PIL import Image

from app.core.config import Settings, get_settings
from app.cv.class_names import CLASS_NAMES, get_disease_name


class PlantDiseaseService:
    def __init__(self, model, settings: Settings | None = None):
        self.model = model
        self.model.eval()
        self.settings = settings or get_settings()

    def predict(self, image_bytes: bytes) -> dict:
        try:
            image = Image.open(BytesIO(image_bytes)).convert("RGB")
        except (OSError, ValueError) as exc:
            raise ValueError("Invalid image data") from exc

        # Import lazily so class-name tooling and lightweight tests do not
        # require torchvision until preprocessing is actually requested.
        from app.cv.preprocessor import prepare_input_image

        image = prepare_input_image(
            image,
            img_size=self.settings.cv_image_size,
        )
        image_tensor = self._preprocess(image).unsqueeze(0)

        with torch.no_grad():
            output = self.model(image_tensor)

        probabilities = torch.softmax(output, dim=1)
        predicted_class = probabilities.argmax(dim=1).item()
        confidence = probabilities[0, predicted_class].item()

        disease_code = CLASS_NAMES[predicted_class]
        disease_name = get_disease_name(disease_code)

        return {
            "class_id": predicted_class,
            "class_name": disease_code,
            "disease_code": disease_code,
            "disease_name": disease_name,
            "confidence": round(confidence, 3),
            "model_name": self.settings.cv_model_name,
            "model_version": self.settings.cv_model_version,
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
