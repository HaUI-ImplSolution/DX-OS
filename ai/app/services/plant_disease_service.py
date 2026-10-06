import math
from io import BytesIO

from PIL import Image, ImageFilter, ImageStat

from app.core.config import Settings, get_settings
from app.cv.contract import class_contract, class_entry
from app.cv.model_profile import MODEL_IMAGE_SIZE, MODEL_NAME, MODEL_VERSION
from app.cv.policy import (
    DETECTION_CONFIDENCE_THRESHOLD,
    IOU_THRESHOLD,
    MIN_IMAGE_DIMENSION,
    MIN_SHARPNESS,
)
from app.cv.triage import CvTriageError

SHARPNESS_KERNEL = ImageFilter.Kernel(
    (3, 3),
    (0, 1, 0, 1, -4, 1, 0, 1, 0),
    scale=1,
    offset=128,
)


def inspect_image(image_bytes: bytes) -> tuple[Image.Image, dict]:
    try:
        with Image.open(BytesIO(image_bytes)) as original:
            if original.format not in {"JPEG", "PNG"}:
                raise ValueError("Only JPEG and PNG images are allowed")
            width, height = original.size
            if width * height > 40_000_000:
                raise ValueError("Image resolution is too large")
            original.load()
            image = original.convert("RGB")
    except (OSError, ValueError, Image.DecompressionBombError) as exc:
        raise ValueError("Invalid image data or unsupported resolution/format") from exc
    reasons = []
    if min(width, height) < MIN_IMAGE_DIMENSION:
        reasons.append(
            f"Ảnh quá nhỏ ({width}x{height}px). Mỗi chiều phải từ {MIN_IMAGE_DIMENSION}px."
        )
    grayscale = image.convert("L")
    brightness = float(ImageStat.Stat(grayscale).mean[0])
    sharpness = 0.0
    if min(width, height) >= 3:
        edges = grayscale.filter(SHARPNESS_KERNEL).crop((1, 1, width - 1, height - 1))
        sharpness = float(ImageStat.Stat(edges).var[0])
    if sharpness < MIN_SHARPNESS:
        reasons.append(
            "Ảnh có dấu hiệu bị mờ hoặc thiếu chi tiết. Vui lòng chụp lại rõ nét hơn."
        )
    if brightness < 20:
        reasons.append("Ảnh quá tối. Hãy chụp ở nơi đủ sáng.")
    if brightness > 245:
        reasons.append("Ảnh quá sáng. Tránh ánh sáng chiếu trực tiếp vào máy.")
    return image, {
        "passed": not reasons,
        "reasons": reasons,
        "width": width,
        "height": height,
        "sharpness": round(sharpness, 3),
        "brightness": round(brightness, 3),
    }


def precheck_image(image_bytes: bytes) -> dict:
    return inspect_image(image_bytes)[1]


class PlantDiseaseService:
    def __init__(self, model, settings: Settings | None = None):
        self.model = model
        self.settings = settings or get_settings()
        self.contract = class_contract(self.settings.cv_class_contract_path)
        self.device = getattr(model, "_dxfarm_device", self.settings.cv_device)

    def predict(self, image_bytes: bytes) -> dict:
        image, quality = inspect_image(image_bytes)
        if not quality["passed"]:
            raise CvTriageError(" ".join(quality["reasons"]))

        results = self.model.predict(
            source=image,
            imgsz=MODEL_IMAGE_SIZE,
            conf=DETECTION_CONFIDENCE_THRESHOLD,
            iou=IOU_THRESHOLD,
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
                    or confidence < DETECTION_CONFIDENCE_THRESHOLD
                    or raw_class_id != class_id
                ):
                    continue
                class_entry(class_id, self.contract)
                detections.append((confidence, class_id))

        if detections:
            confidence, predicted_class = max(detections, key=lambda item: item[0])
            entry = class_entry(predicted_class, self.contract)
        else:
            # The five-class checkpoint has no explicit healthy/non-leaf class. A missing
            # detection is therefore uncertain and must pass through the workflow gate.
            confidence = 0.0
            entry = self.contract["fallback"]

        return {
            "disease_code": entry["disease_code"],
            "disease_name": entry["disease_name"],
            "confidence": round(confidence, 3),
            "model_name": MODEL_NAME,
            "model_version": MODEL_VERSION,
        }
