from pathlib import Path
from io import BytesIO

import torch
from PIL import Image
from fastapi.testclient import TestClient
from fastapi import FastAPI

from app.api.router import router
from app.cv.class_names import CLASS_NAMES
from app.services.plant_disease_service import PlantDiseaseService


TEST_DATA_DIR = Path(__file__).parents[1] / "data" / "images"
TOMATO_LEAF_PATH = TEST_DATA_DIR / "tomato_leaf.jpg"


class FakeModel:
    """Fake model returning deterministic logits for testing."""

    def eval(self):
        return self

    def __call__(self, x):
        output = torch.zeros((x.shape[0], 38))
        # Class 29 = "Tomato___Early_blight"
        output[:, 29] = 10.0
        return output


def _create_dummy_image_bytes() -> bytes:
    img = Image.new("RGB", (224, 224), color="green")
    buf = BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


def test_service_predict_returns_valid_cv_contract():
    """Predict output must strictly match CV contract schema."""

    service = PlantDiseaseService(model=FakeModel())
    image_bytes = _create_dummy_image_bytes()

    result = service.predict(image_bytes)

    assert result["disease_code"] == "Tomato___Early_blight"
    assert result["disease_name"] == "Bệnh cháy lá sớm trên cà chua"
    assert result["class_id"] == 29
    assert result["class_name"] == "Tomato___Early_blight"
    assert isinstance(result["confidence"], float)
    assert result["confidence"] > 0.0
    assert result["model_name"] is not None
    assert result["model_version"] is not None


def test_service_predict_with_real_image():
    """Predict should work properly with a real leaf image file."""

    assert TOMATO_LEAF_PATH.exists(), f"Test image missing: {TOMATO_LEAF_PATH}"
    image_bytes = TOMATO_LEAF_PATH.read_bytes()

    service = PlantDiseaseService(model=FakeModel())
    result = service.predict(image_bytes)

    assert result["disease_code"] in CLASS_NAMES.values()
    assert result["disease_code"] == "Tomato___Early_blight"
    assert result["disease_name"] is not None


def test_api_router_predict_endpoint():
    """FastAPI endpoint /disease/predict should return status 200 and contract JSON."""

    app = FastAPI()
    app.include_router(router)
    app.state.plant_model = FakeModel()

    client = TestClient(app)

    image_bytes = _create_dummy_image_bytes()

    response = client.post(
        "/disease/predict",
        files={"image": ("test.jpg", image_bytes, "image/jpeg")},
    )

    assert response.status_code == 200
    data = response.json()

    assert "disease_code" in data
    assert data["disease_code"] == "Tomato___Early_blight"
    assert "disease_name" in data
    assert data["disease_name"] == "Bệnh cháy lá sớm trên cà chua"
    assert "confidence" in data
    assert "model_name" in data
    assert "model_version" in data
