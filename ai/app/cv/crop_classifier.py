# SPDX-License-Identifier: Apache-2.0
"""Pinned crop classifier; its score is not proof that an image depicts rice."""

import ast

import numpy as np
from PIL import Image

from app.core.config import Settings, get_settings
from app.cv.loader import _sha256

CROP_SHA256 = "b9c34acf76a3d8a04820d7f26c8318736478f54c568a655c09a73f55190837a5"
CROP_CLASSES = (
    "Cabbage",
    "Cauliflower",
    "Chili",
    "Eggplant",
    "Gourd",
    "Guava",
    "Others",
    "Potato",
    "Rice",
    "Tomato",
)
RICE_THRESHOLD = 0.80
RICE_MARGIN = 0.20


class CropInferenceError(RuntimeError):
    """Classifier unavailable or returned an invalid result."""


class CropClassifier:
    def __init__(self, session):
        self.session = session
        inputs, outputs = session.get_inputs(), session.get_outputs()
        if (
            len(inputs) != 1
            or inputs[0].name != "images"
            or inputs[0].shape != [1, 3, 224, 224]
            or inputs[0].type != "tensor(float)"
            or len(outputs) != 1
            or outputs[0].shape != [1, 10]
            or outputs[0].type != "tensor(float)"
        ):
            raise ValueError("Invalid crop classifier input/output contract")
        metadata = session.get_modelmeta().custom_metadata_map
        if metadata.get("task") != "classify" or ast.literal_eval(
            metadata.get("names", "{}")
        ) != dict(enumerate(CROP_CLASSES)):
            raise ValueError("Invalid crop classifier class order/task")

    def probabilities(self, image: Image.Image) -> np.ndarray:
        # Match Ultralytics classify_transforms: shortest-edge resize, center crop,
        # RGB /255. The pinned ONNX already includes Softmax.
        from torchvision.transforms import (
            CenterCrop,
            Compose,
            InterpolationMode,
            Resize,
            ToTensor,
        )

        transform = Compose(
            [
                Resize(224, interpolation=InterpolationMode.BILINEAR),
                CenterCrop(224),
                ToTensor(),
            ]
        )
        tensor = transform(image.convert("RGB")).unsqueeze(0).numpy()
        try:
            result = np.asarray(self.session.run(None, {"images": tensor})[0])
        except Exception as exc:
            raise CropInferenceError(
                "Không thể kiểm tra ảnh lúc này. Vui lòng thử lại."
            ) from exc
        if (
            result.shape != (1, 10)
            or not np.isfinite(result).all()
            or (result < 0).any()
            or (result > 1).any()
            or not np.isclose(result.sum(), 1, atol=1e-4)
        ):
            raise CropInferenceError(
                "Không thể kiểm tra ảnh lúc này. Vui lòng thử lại."
            )
        return result[0]

    def rejection_reason(self, image: Image.Image) -> str | None:
        probabilities = self.probabilities(image)
        rice = float(probabilities[8])
        other = float(np.max(np.delete(probabilities, 8)))
        # ponytail: closed-set classification can accept corn/wheat/grass as Rice;
        # upgrade with a validated model trained on these negative examples.
        if (
            int(probabilities.argmax()) != 8
            or rice < RICE_THRESHOLD
            or rice - other < RICE_MARGIN
        ):
            return "Ảnh chưa được nhận diện là lúa. Vui lòng chụp rõ lá lúa ở giữa khung hình."
        return None


def load_crop_classifier(settings: Settings | None = None) -> CropClassifier:
    settings = settings or get_settings()
    path = settings.crop_classifier_path
    if (
        settings.crop_classifier_sha256 != CROP_SHA256
        or not path.is_file()
        or _sha256(path) != CROP_SHA256
    ):
        raise ValueError(
            "Crop classifier missing or SHA-256 does not match pinned artifact"
        )
    import onnxruntime as ort

    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    return CropClassifier(
        ort.InferenceSession(
            str(path),
            sess_options=options,
            providers=["CPUExecutionProvider"],
        )
    )
