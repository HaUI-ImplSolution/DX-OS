# SPDX-License-Identifier: Apache-2.0
import hashlib
import json
from io import BytesIO
from pathlib import Path

import pytest
import torch
from app.api.schemas import DiseaseDetectionResponse
from app.core.config import Settings
from app.cv.contract import load_class_contract
from app.cv.loader import load_model, validate_model_contract
from app.cv.model_profile import (
    MODEL_IMAGE_SIZE,
    MODEL_MEAN,
    MODEL_NAME,
    MODEL_STD,
    MODEL_VERSION,
)
from app.cv.triage import CvTriageError
from app.services.plant_disease_service import PlantDiseaseService
from conftest import AcceptCrop, leaf_scene
from PIL import Image, ImageDraw, ImageFilter
from scripts.assemble_cv_weights import assemble, verify_parts

CONTRACT_PATH = (
    Path(__file__).resolve().parents[2] / "data/structured/class_contract.json"
)
EXPECTED_RESPONSE_FIELDS = {
    "disease_code",
    "disease_name",
    "confidence",
    "model_name",
    "model_version",
}


class FakeModel:
    _device = "cpu"

    def __init__(self, index=4, confidence=0.93, logits=None):
        probabilities = torch.full((1, 6), (1 - confidence) / 5)
        probabilities[0, index] = confidence
        self.logits = probabilities.log() if logits is None else logits
        self.image = None

    def __call__(self, image):
        assert not torch.is_grad_enabled()
        self.image = image
        return self.logits


def image_bytes(image):
    stream = BytesIO()
    image.save(stream, format="PNG")
    return stream.getvalue()


def settings(**overrides):
    return Settings(cv_class_contract_path=CONTRACT_PATH, **overrides)


def checkpoint_metadata():
    names = [
        row["model_label"] for row in load_class_contract(CONTRACT_PATH)["classes"]
    ]
    return {
        "architecture": "efficientnet_v2_m",
        "img_size": MODEL_IMAGE_SIZE,
        "class_names": names,
        "class_to_idx": dict(zip(names, range(6))),
        "imagenet_mean": list(MODEL_MEAN),
        "imagenet_std": list(MODEL_STD),
    }


@pytest.mark.parametrize(
    "index,code",
    [
        (0, "Rice___healthy"),
        (1, "Rice___Blast"),
        (2, "Rice___Brown_spot"),
        (3, "Rice___Leaf_smut"),
        (4, "Rice___Tungro"),
        (5, "Rice___Sheath_blight"),
    ],
)
def test_classifier_uses_canonical_contract_without_expanding_response(index, code):
    model = FakeModel(index=index)
    result = PlantDiseaseService(model, AcceptCrop(), settings()).predict(
        image_bytes(leaf_scene())
    )
    assert result["disease_code"] == code
    assert result["confidence"] == pytest.approx(0.93)
    assert result["model_name"] == MODEL_NAME
    assert result["model_version"] == MODEL_VERSION
    assert set(result) == EXPECTED_RESPONSE_FIELDS
    assert set(DiseaseDetectionResponse.model_fields) == EXPECTED_RESPONSE_FIELDS
    assert model.image.shape == (1, 3, 480, 480)


def test_transform_matches_checkpoint_recipe():
    from torchvision import transforms

    image = leaf_scene()
    service = PlantDiseaseService(FakeModel(), AcceptCrop(), settings())
    reference = transforms.Compose(
        [
            transforms.Resize(
                (480, 480),
                interpolation=transforms.InterpolationMode.BICUBIC,
                antialias=True,
            ),
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
        ]
    )
    torch.testing.assert_close(
        service.transform(image), reference(image), rtol=0, atol=0
    )


def test_confidence_is_not_rounded_before_workflow_gate():
    result = PlantDiseaseService(
        FakeModel(confidence=0.6999), AcceptCrop(), settings()
    ).predict(image_bytes(leaf_scene()))
    assert 0.6998 < result["confidence"] < 0.70


@pytest.mark.parametrize(
    "logits",
    [
        torch.zeros(1, 5),
        torch.zeros(2, 6),
        torch.full((1, 6), float("nan")),
        torch.full((1, 6), float("inf")),
        torch.zeros(1, 6, dtype=torch.int64),
    ],
)
def test_invalid_output_is_rejected_instead_of_becoming_healthy(logits):
    with pytest.raises(ValueError, match="Invalid model output"):
        PlantDiseaseService(FakeModel(logits=logits), AcceptCrop(), settings()).predict(
            image_bytes(leaf_scene())
        )


@pytest.mark.parametrize(
    "image,reason",
    [
        (Image.new("RGB", (100, 100), "green"), "Ảnh quá nhỏ"),
        (Image.new("RGB", (256, 256), "green"), "bị mờ"),
    ],
)
def test_quality_rejection_happens_before_inference(image, reason):
    model = FakeModel()
    with pytest.raises(CvTriageError, match=reason):
        PlantDiseaseService(model, AcceptCrop(), settings()).predict(image_bytes(image))
    assert model.image is None


def test_blurred_subject_with_screen_detail_is_rejected_before_inference():
    image = leaf_scene().filter(ImageFilter.GaussianBlur(4)).resize((1280, 960))
    draw = ImageDraw.Draw(image)
    for x in range(0, image.width, 2):
        draw.line((x, 0, x, 959), fill=(165, 165, 165))
    model = FakeModel()
    with pytest.raises(CvTriageError, match="bị mờ"):
        PlantDiseaseService(model, AcceptCrop(), settings()).predict(image_bytes(image))
    assert model.image is None


def test_invalid_image_keeps_existing_error_behavior():
    with pytest.raises(ValueError, match="Invalid image data"):
        PlantDiseaseService(FakeModel(), AcceptCrop(), settings()).predict(
            b"not-an-image"
        )


def test_checkpoint_metadata_matches_contract():
    validate_model_contract(checkpoint_metadata(), load_class_contract(CONTRACT_PATH))


@pytest.mark.parametrize(
    "key,value",
    [
        ("class_names", ["Wrong class"] * 6),
        ("architecture", "efficientnet_b0"),
        ("img_size", 224),
        ("imagenet_std", [1, 1, 1]),
    ],
)
def test_checkpoint_metadata_mismatch_is_rejected(key, value):
    metadata = checkpoint_metadata()
    metadata[key] = value
    with pytest.raises(ValueError, match="classes or preprocessing"):
        validate_model_contract(metadata, load_class_contract(CONTRACT_PATH))


def test_checksum_mismatch_fails_before_model_construction(monkeypatch):
    monkeypatch.setattr("app.cv.loader._sha256", lambda _path: "1" * 64)
    with pytest.raises(ValueError, match="SHA-256"):
        load_model(settings(cv_weights_path=CONTRACT_PATH, cv_weights_sha256="0" * 64))


@pytest.mark.parametrize("checksum", [None, "", "not-a-sha256", "g" * 64])
def test_missing_or_invalid_checksum_configuration_fails(checksum):
    with pytest.raises(ValueError, match="CV_WEIGHTS_SHA256"):
        load_model(settings(cv_weights_sha256=checksum))


def test_assembly_checks_parts_and_does_not_replace_output_on_failure(
    tmp_path, monkeypatch
):
    chunks = [b"first", b"second", b"last"]
    parts = [
        {"name": f"model.pth.part-{i:03d}", "bytes": len(chunk)}
        for i, chunk in enumerate(chunks)
    ]
    for part, chunk in zip(parts, chunks):
        (tmp_path / part["name"]).write_bytes(chunk)
    (tmp_path / "parts.json").write_text(
        json.dumps({"parts": parts, "bytes": sum(map(len, chunks))})
    )
    checksum = hashlib.sha256(b"".join(chunks)).hexdigest()
    monkeypatch.setenv("CV_WEIGHTS_SHA256", checksum)
    output = assemble(tmp_path, tmp_path / "model.pth")
    assert output.read_bytes() == b"".join(chunks)
    (tmp_path / parts[1]["name"]).write_bytes(b"x" * len(chunks[1]))
    with pytest.raises(ValueError, match="checksum mismatch"):
        assemble(tmp_path, output)
    assert output.read_bytes() == b"".join(chunks)
    assert not list(tmp_path.glob(".cv-*"))
    parts.reverse()
    (tmp_path / "parts.json").write_text(
        json.dumps({"parts": parts, "bytes": sum(map(len, chunks))})
    )
    with pytest.raises(ValueError, match="ordered"):
        verify_parts(tmp_path)
