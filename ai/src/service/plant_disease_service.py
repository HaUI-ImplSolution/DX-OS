from io import BytesIO

import torch
from PIL import Image

from src.models.dataset import prepare_input_image
from src.models.class_names import CLASS_NAMES


class PlantDiseaseService:
    MODEL_NAME = "EfficientNet-B4"
    MODEL_VERSION = "v1.0"

    def __init__(self, model):
        self.model = model
        self.model.eval()

    def predict(self, image_bytes: bytes) -> dict:
        try:
            image = Image.open(BytesIO(image_bytes)).convert("RGB")
        except Exception as e:
            raise ValueError(f"Invalid image: {str(e)}")

        image = prepare_input_image(
            image,
            img_size=224,
        )

        image_tensor = self._preprocess(image)
        image_tensor = image_tensor.unsqueeze(0)

        with torch.no_grad():
            output = self.model(image_tensor)

        probabilities = torch.softmax(output, dim=1)

        predicted_class = probabilities.argmax(dim=1).item()
        confidence = probabilities[0, predicted_class].item()

        return {
            "class_id": predicted_class,
            "class_name": CLASS_NAMES[predicted_class],
            "disease_code": None,
            "disease_name_vi": None,
            "confidence": round(confidence, 3),
            "model_name": self.MODEL_NAME,
            "model_version": self.MODEL_VERSION,
        }

    @staticmethod
    def _preprocess(image: Image.Image) -> torch.Tensor:
        from torchvision import transforms

        transform = transforms.Compose([
            transforms.ToTensor(),
            transforms.Normalize(
                mean=[0.485, 0.456, 0.406],
                std=[0.229, 0.224, 0.225],
            ),
        ])

        return transform(image)