# SPDX-License-Identifier: Apache-2.0
import json
from io import BytesIO
from pathlib import Path

import pytest
import torch
from fastapi import FastAPI
from fastapi.testclient import TestClient
from PIL import Image

from src.api.router import router
from src.cv.class_names import CLASS_NAMES
from src.cv.contract import canonical_code, class_contract
from src.services.plant_disease_service import PlantDiseaseService


class Model(torch.nn.Module):
    def __init__(self, index=8):
        super().__init__()
        self.index = index

    def forward(self, x):
        result = torch.zeros((1, 38), device=x.device)
        result[:, self.index] = 10
        return result


def image_bytes():
    buf = BytesIO()
    Image.new("RGB", (224, 224), "green").save(buf, format="PNG")
    return buf.getvalue()


def test_contract_matches_all_source_codes_and_training_order():
    root = Path(__file__).resolve().parents[2]
    diseases_file = root / "data/structured/diseases.json"
    if not diseases_file.exists():
        pytest.skip("Structured diseases JSON not included.")
    diseases = json.loads(diseases_file.read_text(encoding="utf-8"))
    contract = class_contract()
    assert len(contract["classes"]) == 38
    assert {c["disease_code"] for c in contract["classes"]} == {
        d["code"] for d in diseases
    }
    from src.cv.preprocessor import CLASSES

    assert list(CLASS_NAMES.values()) == CLASSES
    assert (
        json.loads((root / "data/structured/class_contract.json").read_text(encoding="utf-8"))
        == contract
    )


def test_common_rust_adapter_preserves_model_label():
    result = PlantDiseaseService(Model()).predict(image_bytes())
    assert result["disease_code"] == "Corn_(maize)__Common_rust"
    assert result["class_name"] == "Corn_(maize)___Common_rust_"
    assert 0 <= result["confidence"] <= 1
    assert result["model_name"] and result["model_version"]
    with pytest.raises(ValueError):
        canonical_code("typo")


def test_health_predict_and_error_contract():
    app = FastAPI()
    app.include_router(router, prefix="/api/v1")
    with TestClient(app) as client:
        assert client.get("/api/v1/disease/health").json()["model_loaded"] is False
        url = "/api/v1/disease/predict"
        assert (
            client.post(
                url, files={"image": ("x.png", image_bytes(), "image/png")}
            ).status_code
            == 503
        )
        app.state.plant_model = Model()
        assert client.get("/api/v1/disease/health").json()["model_loaded"] is True
        response = client.post(url, files={"image": ("x.png", image_bytes(), "image/png")})
        assert response.status_code == 200
        assert response.json()["disease_code"] == "Corn_(maize)__Common_rust"
        for mime in ("image/png", "text/plain"):
            assert (
                client.post(url, files={"image": ("bad", b"invalid", mime)}).status_code
                == 400
            )
        assert len(client.get("/api/v1/disease/class-contract").json()["classes"]) == 38
        # Stage 8 endpoint exists; multipart image and context are required.
        assert client.post("/api/v1/disease/workflow/run").status_code == 422
