# SPDX-License-Identifier: Apache-2.0
from io import BytesIO

import numpy as np
import pytest
from conftest import AcceptCrop, leaf_scene
from fastapi import FastAPI
from fastapi.testclient import TestClient
from PIL import Image, ImageDraw, ImageFilter

from app.api.router import router
from app.core.config import Settings, get_settings
from app.db.session import get_db
from app.services.plant_disease_service import inspect_image
from app.services.symptom_service import suggest_diseases


def precheck_image(data):
    return inspect_image(data)[1]


def encode(image):
    stream = BytesIO()
    image.save(stream, format="PNG")
    return stream.getvalue()


def client():
    app = FastAPI()
    app.state.crop_classifier = AcceptCrop()
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


@pytest.mark.parametrize("size", [(640, 480), (1280, 960)])
def test_quality_accepts_focused_detail_at_different_resolutions(size):
    image = leaf_scene().resize(size, Image.Resampling.LANCZOS)
    assert precheck_image(encode(image))["passed"] is True


def test_quality_accepts_focused_photo_after_jpeg_compression():
    stream = BytesIO()
    leaf_scene().save(stream, format="JPEG", quality=70)
    assert precheck_image(stream.getvalue())["passed"] is True


def test_quality_accepts_focused_detail_with_camera_noise():
    pixels = np.asarray(leaf_scene(), dtype=np.float32)
    grain = np.random.default_rng(6).normal(0, 12, (480, 640, 1))
    noisy = Image.fromarray(np.clip(pixels + grain, 0, 255).astype(np.uint8))
    assert precheck_image(encode(noisy))["passed"] is True


@pytest.mark.parametrize("radius", [2, 4, 8])
def test_quality_rejects_defocused_subject(radius):
    quality = precheck_image(
        encode(leaf_scene().filter(ImageFilter.GaussianBlur(radius)))
    )
    assert quality["passed"] is False
    assert any("mờ" in reason for reason in quality["reasons"])


def test_sharp_caption_and_border_do_not_hide_blurry_subject():
    image = leaf_scene().filter(ImageFilter.GaussianBlur(4))
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 639, 479), outline="white", width=4)
    for x in range(0, 640, 12):
        draw.text((x, 12), "TEXT", fill="white")
    assert precheck_image(encode(image))["passed"] is False


@pytest.mark.parametrize("period", [2, 3])
def test_screen_pixel_stripes_do_not_hide_blurry_subject(period):
    image = leaf_scene().filter(ImageFilter.GaussianBlur(4)).resize((1280, 960))
    draw = ImageDraw.Draw(image)
    for x in range(0, image.width, period):
        draw.line((x, 0, x, image.height - 1), fill=(165, 165, 165), width=1)
    quality = precheck_image(encode(image))
    assert quality["passed"] is False
    assert any("mờ" in reason for reason in quality["reasons"])


@pytest.mark.parametrize("noise", [12, 24])
def test_camera_noise_does_not_hide_blurry_subject(noise):
    pixels = np.asarray(
        leaf_scene().filter(ImageFilter.GaussianBlur(4)), dtype=np.float32
    )
    grain = np.random.default_rng(6).normal(0, noise, (480, 640, 1))
    noisy = Image.fromarray(np.clip(pixels + grain, 0, 255).astype(np.uint8))
    assert precheck_image(encode(noisy))["passed"] is False


def test_precheck_api_rejects_blur_with_sharp_overlay():
    image = leaf_scene().filter(ImageFilter.GaussianBlur(4))
    draw = ImageDraw.Draw(image)
    draw.text((0, 0), "SHARP SCREEN CAPTION", fill="white")
    response = client().post(
        "/api/v1/disease/precheck",
        headers={"X-Service-Api-Key": "test-key"},
        files={"image": ("screen.png", encode(image), "image/png")},
    )
    assert response.status_code == 200
    assert response.json()["passed"] is False
    assert any("mờ" in reason for reason in response.json()["reasons"])


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
