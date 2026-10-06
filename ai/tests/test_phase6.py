from io import BytesIO

import pytest
from app.api.router import router
from app.core.config import Settings, get_settings
from app.db.session import get_db
from app.services.plant_disease_service import precheck_image
from app.services.symptom_service import suggest_diseases
from fastapi import FastAPI
from fastapi.testclient import TestClient
from PIL import Image


def encode(image):
    stream = BytesIO()
    image.save(stream, format="PNG")
    return stream.getvalue()


def client():
    app = FastAPI()
    app.include_router(router, prefix="/api/v1")
    app.dependency_overrides[get_settings] = lambda: Settings(
        ai_service_api_key="test-key", _env_file=None
    )
    app.dependency_overrides[get_db] = lambda: object()
    return TestClient(app)


def test_precheck_blurry_image_returns_reasons_without_loading_model():
    response = client().post(
        "/api/v1/disease/precheck",
        headers={"X-Service-Api-Key": "test-key"},
        files={
            "image": (
                "blur.png",
                encode(Image.new("RGB", (256, 256), "green")),
                "image/png",
            )
        },
    )
    assert response.status_code == 200
    assert response.json()["passed"] is False
    assert any("mờ" in reason for reason in response.json()["reasons"])
    assert response.json()["sharpness"] == 0.0


def test_precheck_and_suggestions_require_backend_authentication():
    c = client()
    assert (
        c.post(
            "/api/v1/disease/precheck",
            files={"image": ("x.png", b"invalid", "image/png")},
        ).status_code
        == 401
    )
    assert (
        c.post(
            "/api/v1/disease/symptoms/suggest", json={"symptom_text": "vết bệnh"}
        ).status_code
        == 401
    )


@pytest.mark.parametrize("color,reason", [("black", "tối"), ("white", "sáng")])
def test_quality_rejects_bad_exposure(color, reason):
    quality = precheck_image(encode(Image.new("RGB", (256, 256), color)))
    assert quality["passed"] is False
    assert any(reason in item for item in quality["reasons"])


def test_quality_checks_dimensions_and_rejects_invalid_bytes():
    assert not precheck_image(encode(Image.new("RGB", (100, 100), "green")))["passed"]
    with pytest.raises(ValueError):
        precheck_image(b"invalid")


def test_symptom_ranking_filters_unknown_codes_and_keeps_best_evidence():
    class Retriever:
        def retrieve(self, db, query, top_k):
            assert top_k == 20
            return [
                {"disease_code": "made_up", "similarity": 1, "content": "invented"},
                {"disease_code": "Rice___Blast", "similarity": 0.5, "content": "old"},
                {
                    "disease_code": "Rice___Brown_spot",
                    "similarity": 0.7,
                    "content": "brown",
                },
                {
                    "disease_code": "Rice___Blast",
                    "similarity": 0.9,
                    "content": "blast",
                    "source_ids": ["IRRI"],
                },
            ]

    result = suggest_diseases(object(), "vết hình thoi", retriever=Retriever())
    assert [item["disease_code"] for item in result] == [
        "Rice___Blast",
        "Rice___Brown_spot",
    ]
    assert result[0]["evidence"] == "blast"
    assert result[0]["source_ids"] == ["IRRI"]
