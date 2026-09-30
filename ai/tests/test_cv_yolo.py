from io import BytesIO
from pathlib import Path
from typing import ClassVar

import pytest
from PIL import Image

from app.api.schemas import DiseaseDetectionResponse
from app.core.config import Settings
from app.cv.contract import load_class_contract
from app.cv.loader import load_model, validate_model_contract
from app.services.plant_disease_service import PlantDiseaseService

PROJECT_ROOT = Path(__file__).resolve().parents[2]
CONTRACT_PATH = PROJECT_ROOT / "data" / "structured" / "class_contract.json"
EXPECTED_RESPONSE_FIELDS = {
    "disease_code",
    "disease_name",
    "confidence",
    "model_name",
    "model_version",
}


class FakeValues:
    def __init__(self, values):
        self.values = values

    def detach(self):
        return self

    def cpu(self):
        return self

    def tolist(self):
        return self.values


class FakeBoxes:
    def __init__(self, confidences, class_ids):
        self.conf = FakeValues(confidences)
        self.cls = FakeValues(class_ids)

    def __len__(self):
        return len(self.conf.values)


class FakeResult:
    def __init__(self, confidences, class_ids):
        self.boxes = FakeBoxes(confidences, class_ids)


class FakeModel:
    task = "detect"
    names: ClassVar[dict[int, str]] = {
        0: "Blast",
        1: "Brown Spot",
        2: "Leaf Smut",
        3: "Rice Tungro",
        4: "Sheath Blight",
    }
    _dxfarm_device = "cpu"

    def __init__(self, confidences, class_ids):
        self.result = FakeResult(confidences, class_ids)
        self.predict_kwargs = None

    def predict(self, **kwargs):
        self.predict_kwargs = kwargs
        return [self.result]


def image_bytes() -> bytes:
    stream = BytesIO()
    Image.new("RGB", (32, 32), color="green").save(stream, format="PNG")
    return stream.getvalue()


def settings(**overrides) -> Settings:
    values = {
        "cv_class_contract_path": CONTRACT_PATH,
        "cv_weights_sha256": None,
        **overrides,
    }
    return Settings(**values)


def test_highest_confidence_detection_uses_canonical_contract():
    model = FakeModel([0.61, 0.93, 0.82], [0.0, 3.0, 1.0])

    result = PlantDiseaseService(model, settings()).predict(image_bytes())

    assert result == {
        "disease_code": "Rice___Tungro",
        "disease_name": "Bệnh tungro trên lúa",
        "confidence": 0.93,
        "model_name": "YOLOv8n-RiceLeafBD",
        "model_version": "3.0.0",
    }
    assert set(result) == EXPECTED_RESPONSE_FIELDS
    assert set(DiseaseDetectionResponse.model_fields) == EXPECTED_RESPONSE_FIELDS
    assert model.predict_kwargs["conf"] == 0.25
    assert model.predict_kwargs["iou"] == 0.45


def test_no_detection_uses_healthy_fallback_without_expanding_response():
    result = PlantDiseaseService(FakeModel([], []), settings()).predict(image_bytes())

    assert result == {
        "disease_code": "Rice___healthy",
        "disease_name": "Lá lúa không thấy triệu chứng bệnh",
        "confidence": 1.0,
        "model_name": "YOLOv8n-RiceLeafBD",
        "model_version": "3.0.0",
    }
    assert set(result) == EXPECTED_RESPONSE_FIELDS


def test_detection_below_threshold_uses_healthy_fallback():
    result = PlantDiseaseService(FakeModel([0.24], [0.0]), settings()).predict(
        image_bytes()
    )

    assert result["disease_code"] == "Rice___healthy"
    assert result["confidence"] == 1.0


def test_invalid_image_keeps_existing_error_behavior():
    with pytest.raises(ValueError, match="Invalid image data"):
        PlantDiseaseService(FakeModel([], []), settings()).predict(b"not-an-image")


def test_model_class_order_mismatch_fails_validation():
    model = FakeModel([], [])
    model.names = dict(model.names)
    model.names[1] = "Wrong class"

    with pytest.raises(ValueError, match="class order"):
        validate_model_contract(model, load_class_contract(CONTRACT_PATH), 5)


def test_model_task_mismatch_fails_validation():
    model = FakeModel([], [])
    model.task = "classify"

    with pytest.raises(ValueError, match="task must be 'detect'"):
        validate_model_contract(model, load_class_contract(CONTRACT_PATH), 5)


def test_checksum_mismatch_fails_before_model_import(monkeypatch):
    monkeypatch.setattr("app.cv.loader._sha256", lambda _path: "1" * 64)

    with pytest.raises(ValueError, match="SHA-256"):
        load_model(
            settings(
                cv_weights_path=CONTRACT_PATH,
                cv_weights_sha256="0" * 64,
            )
        )
