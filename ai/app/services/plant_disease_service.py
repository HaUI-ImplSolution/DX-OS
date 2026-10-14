# SPDX-License-Identifier: Apache-2.0
import math
from io import BytesIO

import numpy as np
from PIL import Image, ImageFilter, ImageOps, ImageStat

from app.core.config import Settings, get_settings
from app.cv.contract import class_contract, class_entry
from app.cv.model_profile import MODEL_IMAGE_SIZE, MODEL_NAME, MODEL_VERSION
from app.cv.policy import (
    DETECTION_CONFIDENCE_THRESHOLD,
    IOU_THRESHOLD,
    MIN_IMAGE_DIMENSION,
    MIN_SHARPNESS,
    PLAIN_BACKGROUND_EDGE_FLOOR,
    PLAIN_BACKGROUND_MIN_EDGE_LOSS,
    PLAIN_BACKGROUND_MIN_EDGE_PIXELS,
    PLAIN_BACKGROUND_MIN_FLAT_TILES,
    PLAIN_BACKGROUND_REBLUR_RADIUS,
    SHARPNESS_GRID_SIZE,
    SHARPNESS_MAX_EDGE,
)
from app.cv.triage import CvTriageError


def _laplacian(pixels: np.ndarray) -> np.ndarray:
    return (
        pixels[:-2, 1:-1]
        + pixels[2:, 1:-1]
        + pixels[1:-1, :-2]
        + pixels[1:-1, 2:]
        - 4 * pixels[1:-1, 1:-1]
    )


GAUSSIAN_WEIGHTS = np.array([1, 4, 6, 4, 1], dtype=np.float32) / 16
# Exact variance gain of the smoothing + Laplacian kernel for independent noise.
LAPLACIAN_NOISE_GAIN = float(
    np.square(_laplacian(np.pad(np.outer(GAUSSIAN_WEIGHTS, GAUSSIAN_WEIGHTS), 2))).sum()
)
NORMAL_MAD_SCALE = 0.6744897501960817


def _noise_variance(pixels: np.ndarray) -> float:
    height, width = pixels.shape
    even = pixels[: height // 2 * 2, : width // 2 * 2]
    # Diagonal differences cancel smooth detail; MAD limits sharp-edge influence.
    diagonal = (
        even[::2, ::2] + even[1::2, 1::2] - even[::2, 1::2] - even[1::2, ::2]
    ) / 2
    sigma = float(np.median(np.abs(diagonal - np.median(diagonal)))) / NORMAL_MAD_SCALE
    return sigma * sigma


def image_sharpness(grayscale: Image.Image) -> float:
    # Central-detail heuristic: it cannot isolate leaves, a leaf ROI would be the upgrade.
    # Suppress screen pixels/noise and compare detail at a bounded resolution.
    normalized = grayscale.copy()
    normalized.thumbnail(
        (SHARPNESS_MAX_EDGE, SHARPNESS_MAX_EDGE), Image.Resampling.LANCZOS
    )
    pixels = np.asarray(normalized, dtype=np.float32)
    height, width = pixels.shape
    margin_y, margin_x = height // 10, width // 10
    pixels = pixels[margin_y : height - margin_y, margin_x : width - margin_x]
    if min(pixels.shape) < SHARPNESS_GRID_SIZE + 2:
        return 0.0
    noise = _noise_variance(pixels)
    height, width = pixels.shape
    # Keep smoothing and edge detection in float, without 8-bit clipping/rounding.
    padded = np.pad(pixels, ((0, 0), (2, 2)), mode="reflect")
    pixels = sum(
        weight * padded[:, i : i + width] for i, weight in enumerate(GAUSSIAN_WEIGHTS)
    )
    padded = np.pad(pixels, ((2, 2), (0, 0)), mode="reflect")
    pixels = sum(
        weight * padded[i : i + height, :] for i, weight in enumerate(GAUSSIAN_WEIGHTS)
    )
    laplacian = _laplacian(pixels)
    # A sharp border or caption must not outweigh an otherwise blurry subject.
    scores = [
        float(tile.var())
        for row in np.array_split(laplacian, SHARPNESS_GRID_SIZE)
        for tile in np.array_split(row, SHARPNESS_GRID_SIZE, axis=1)
    ]
    return max(0.0, float(np.median(scores)) - noise * LAPLACIAN_NOISE_GAIN)


def _edge_magnitude(pixels: np.ndarray) -> np.ndarray:
    return (
        np.abs(np.diff(pixels, axis=1))[:-1, :]
        + np.abs(np.diff(pixels, axis=0))[:, :-1]
    )


def sharp_on_plain_background(grayscale: Image.Image) -> bool:
    # Plain-background images are judged by the edge energy a re-blur removes: large when
    # sharp, small when blurry. Noise and stripes put edges in every tile, so they never
    # count as a plain background.
    normalized = grayscale.copy()
    normalized.thumbnail(
        (SHARPNESS_MAX_EDGE, SHARPNESS_MAX_EDGE), Image.Resampling.LANCZOS
    )
    reblurred = normalized.filter(
        ImageFilter.GaussianBlur(radius=PLAIN_BACKGROUND_REBLUR_RADIUS)
    )
    pixels = np.asarray(normalized, dtype=np.float32)
    height, width = pixels.shape
    inner = (
        slice(height // 10, height - height // 10),
        slice(width // 10, width - width // 10),
    )
    edges = _edge_magnitude(pixels[inner])
    softened = _edge_magnitude(np.asarray(reblurred, dtype=np.float32)[inner])
    flat_tiles, losses = 0, []
    for edge_row, soft_row in zip(
        np.array_split(edges, SHARPNESS_GRID_SIZE),
        np.array_split(softened, SHARPNESS_GRID_SIZE),
    ):
        for edge, soft in zip(
            np.array_split(edge_row, SHARPNESS_GRID_SIZE, axis=1),
            np.array_split(soft_row, SHARPNESS_GRID_SIZE, axis=1),
        ):
            strong = edge > PLAIN_BACKGROUND_EDGE_FLOOR
            if strong.sum() < PLAIN_BACKGROUND_MIN_EDGE_PIXELS:
                flat_tiles += 1
            else:
                lost = (edge[strong] - soft[strong]).clip(min=0).sum()
                losses.append(float(lost / edge[strong].sum()))
    return (
        flat_tiles >= PLAIN_BACKGROUND_MIN_FLAT_TILES
        and len(losses) >= 2
        and float(np.median(losses)) >= PLAIN_BACKGROUND_MIN_EDGE_LOSS
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
            image = ImageOps.exif_transpose(original).convert("RGB")
            width, height = image.size
    except (OSError, ValueError, Image.DecompressionBombError) as exc:
        raise ValueError("Invalid image data or unsupported resolution/format") from exc
    reasons = []
    if min(width, height) < MIN_IMAGE_DIMENSION:
        reasons.append(
            f"Ảnh quá nhỏ ({width}x{height}px). Mỗi chiều phải từ {MIN_IMAGE_DIMENSION}px."
        )
    grayscale = image.convert("L")
    brightness = float(ImageStat.Stat(grayscale).mean[0])
    sharpness = image_sharpness(grayscale)
    if sharpness < MIN_SHARPNESS and not sharp_on_plain_background(grayscale):
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


def assess_image(image_bytes: bytes, crop_classifier) -> tuple[Image.Image, dict]:
    image, quality = inspect_image(image_bytes)
    if quality["passed"]:
        reason = crop_classifier.rejection_reason(image)
        if reason:
            quality["passed"] = False
            quality["reasons"].append(reason)
    return image, quality


def precheck_image(image_bytes: bytes, crop_classifier) -> dict:
    return assess_image(image_bytes, crop_classifier)[1]


class PlantDiseaseService:
    def __init__(self, model, crop_classifier, settings: Settings | None = None):
        self.model = model
        self.crop_classifier = crop_classifier
        self.settings = settings or get_settings()
        self.contract = class_contract(self.settings.cv_class_contract_path)
        self.device = getattr(model, "_device", self.settings.cv_device)

    def predict(self, image_bytes: bytes) -> dict:
        image, quality = assess_image(image_bytes, self.crop_classifier)
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
