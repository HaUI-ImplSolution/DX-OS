import math
from io import BytesIO

from PIL import Image

from app.core.config import Settings, get_settings
from app.cv.contract import class_contract, class_entry


class PlantDiseaseService:
    def __init__(self, model, settings: Settings | None = None):
        self.model = model
        self.settings = settings or get_settings()
        self.contract = class_contract(self.settings.cv_class_contract_path)
        self.device = getattr(model, "_dxfarm_device", self.settings.cv_device)

    def predict(self, image_bytes: bytes) -> dict:
        try:
            image = Image.open(BytesIO(image_bytes)).convert("RGB")
        except (OSError, ValueError) as exc:
            raise ValueError("Invalid image data") from exc

        results = self.model.predict(
            source=image,
            imgsz=self.settings.cv_image_size,
            conf=self.settings.cv_detection_confidence_threshold,
            iou=self.settings.cv_iou_threshold,
            device=self.device,
            verbose=False,
        )
        if not isinstance(results, list) or len(results) != 1:
            raise ValueError("Invalid model output")

        boxes = getattr(results[0], "boxes", None)
        detections: list[tuple[float, int]] = []
        if boxes is not None and len(boxes):
            confidences = boxes.conf.detach().cpu().tolist()
            class_ids = boxes.cls.detach().cpu().tolist()
            if len(confidences) != len(class_ids):
                raise ValueError("Invalid model output")
            for raw_confidence, raw_class_id in zip(confidences, class_ids):
                confidence = float(raw_confidence)
                class_id = int(raw_class_id)
                if (
                    not math.isfinite(confidence)
                    or confidence < self.settings.cv_detection_confidence_threshold
                    or raw_class_id != class_id
                ):
                    continue
                class_entry(class_id, self.contract)
                detections.append((confidence, class_id))

        if detections:
            confidence, predicted_class = max(detections, key=lambda item: item[0])
            entry = class_entry(predicted_class, self.contract)
        else:
            confidence = 1.0
            entry = self.contract["fallback"]

        return {
            "disease_code": entry["disease_code"],
            "disease_name": entry["disease_name"],
            "confidence": round(confidence, 3),
            "model_name": self.settings.cv_model_name,
            "model_version": self.settings.cv_model_version,
        }
