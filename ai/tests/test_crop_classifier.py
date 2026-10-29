# SPDX-License-Identifier: Apache-2.0
import hashlib
from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace

import numpy as np
import pytest
from conftest import leaf_scene
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api.router import router
from app.core.config import Settings, get_settings
from app.cv.crop_classifier import (
    CROP_CLASSES,
    CropClassifier,
    CropInferenceError,
    load_crop_classifier,
)
from app.cv.triage import CvTriageError
from app.services.plant_disease_service import PlantDiseaseService, precheck_image


class Session:
    def __init__(self, probabilities):
        self.output = np.array([probabilities], dtype=np.float32)

    def get_inputs(self):
        return [
            SimpleNamespace(name="images", shape=[1, 3, 224, 224], type="tensor(float)")
        ]

    def get_outputs(self):
        return [SimpleNamespace(shape=[1, 10], type="tensor(float)")]

    def get_modelmeta(self):
        return SimpleNamespace(
            custom_metadata_map={
                "task": "classify",
                "names": repr(dict(enumerate(CROP_CLASSES))),
            }
        )

    def run(self, _, inputs):
        self.tensor = inputs["images"]
        return [self.output]


def encode(image):
    stream = BytesIO()
    image.save(stream, format="PNG")
    return stream.getvalue()


def crop(rice=0.9):
    values = [0.0] * 10
    values[8], values[0] = rice, 1 - rice
    return CropClassifier(Session(values))


@pytest.mark.parametrize(
    "rice,accepted", [(0.9, True), (0.79, False), (0.6, False), (0.1, False)]
)
def test_rice_policy(rice, accepted):
    assert (crop(rice).rejection_reason(leaf_scene()) is None) == accepted


def test_precheck_and_predict_share_crop_gate():
    classifier = crop(0.1)
    data = encode(leaf_scene())
    assert precheck_image(data, classifier)["passed"] is False
    with pytest.raises(CvTriageError, match="chưa được nhận diện là lúa"):
        PlantDiseaseService(object(), classifier).predict(data)


def test_quality_rejection_does_not_run_classifier():
    assert (
        precheck_image(encode(leaf_scene().resize((100, 100))), object())["passed"]
        is False
    )


def test_probability_output_and_preprocessing():
    classifier = crop()
    probabilities = classifier.probabilities(leaf_scene())
    assert probabilities[8] == pytest.approx(0.9)  # no second Softmax
    assert classifier.session.tensor.shape == (1, 3, 224, 224)
    assert classifier.session.tensor.dtype == np.float32
    assert classifier.session.tensor.min() >= 0 and classifier.session.tensor.max() <= 1
    classifier.session.output[0, 0] = np.nan
    with pytest.raises(CropInferenceError):
        classifier.probabilities(leaf_scene())


def test_runtime_failure_is_service_error():
    classifier = crop()

    def fail(*args):
        raise RuntimeError("runtime unavailable")

    classifier.session.run = fail
    with pytest.raises(CropInferenceError):
        classifier.rejection_reason(leaf_scene())


def test_loader_fails_closed_on_missing_artifact():
    with pytest.raises(ValueError, match="missing or empty"):
        load_crop_classifier(
            Settings(
                crop_classifier_path="/missing/model.onnx",
                crop_classifier_sha256="0" * 64,
                _env_file=None,
            )
        )


@pytest.mark.parametrize("kind", ["empty", "directory"])
def test_loader_rejects_empty_file_and_directory(kind):
    with TemporaryDirectory() as directory:
        path = Path(directory) / "model.onnx"
        if kind == "empty":
            path.touch()
        else:
            path.mkdir()
        with pytest.raises(ValueError, match="missing or empty"):
            load_crop_classifier(
                Settings(
                    crop_classifier_path=path,
                    crop_classifier_sha256="0" * 64,
                    _env_file=None,
                )
            )


def test_loader_reads_local_artifact_only_with_matching_checksum(monkeypatch):
    import onnxruntime as ort

    calls = []

    def session(path, **kwargs):
        calls.append((path, kwargs["providers"]))
        return Session([0.1] * 10)

    monkeypatch.setattr(ort, "InferenceSession", session)
    with TemporaryDirectory() as directory:
        path = Path(directory) / "model.onnx"
        path.write_bytes(b"local artifact supplied to runtime")
        for checksum, message in [
            (None, "must be configured"),
            ("0" * 64, "does not match"),
        ]:
            with pytest.raises(ValueError, match=message):
                load_crop_classifier(
                    Settings(
                        crop_classifier_path=path,
                        crop_classifier_sha256=checksum,
                        _env_file=None,
                    )
                )
        classifier = load_crop_classifier(
            Settings(
                crop_classifier_path=path,
                crop_classifier_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                _env_file=None,
            )
        )
        assert isinstance(classifier, CropClassifier)
        assert calls == [(str(path), ["CPUExecutionProvider"])]


def test_wrong_class_order_or_shape_fails_startup():
    session = Session([0.1] * 10)
    session.get_modelmeta = lambda: SimpleNamespace(
        custom_metadata_map={"task": "classify", "names": "{}"}
    )
    with pytest.raises(ValueError, match="class order"):
        CropClassifier(session)
    session.get_inputs = list
    with pytest.raises(ValueError, match="contract"):
        CropClassifier(session)


def test_http_crop_rejection_and_service_unavailable_are_distinct():
    app = FastAPI()
    app.include_router(router, prefix="/api/v1")
    app.state.crop_classifier = crop(0.1)
    app.state.plant_model = object()
    app.dependency_overrides[get_settings] = lambda: Settings(
        ai_service_api_key="test", _env_file=None
    )
    client = TestClient(app)
    kwargs = {
        "headers": {"X-Service-Api-Key": "test"},
        "files": {"image": ("x.png", encode(leaf_scene()), "image/png")},
    }
    response = client.post("/api/v1/disease/precheck", **kwargs)
    assert response.status_code == 200 and response.json()["passed"] is False
    assert client.post("/api/v1/disease/predict", **kwargs).status_code == 422
    app.state.crop_classifier.session.output[:] = np.nan
    assert client.post("/api/v1/disease/precheck", **kwargs).status_code == 503
    app.state.crop_classifier = None
    assert client.get("/api/v1/disease/health").json()["model_loaded"] is False
    assert client.post("/api/v1/disease/precheck", **kwargs).status_code == 503


def test_crop_rejection_stops_diagnosis_before_backend_and_rag():
    from app.api.schemas import DiagnoseRequest
    from app.services.inference_service import run_stateless_diagnose

    payload = DiagnoseRequest(
        request_id="crop-rejection",
        crop_season_id=1,
        image_url="http://minio/reports/x.jpg",
        field_context={"parcel_code": "P001"},
    )
    # Bare objects fail if downstream services are called at all.
    with pytest.raises(CvTriageError, match="chưa được nhận diện là lúa"):
        run_stateless_diagnose(
            payload=payload,
            image_bytes=encode(leaf_scene()),
            predictor=PlantDiseaseService(object(), crop(0.1)),
            db=object(),
            backend_client=object(),
            rag_retriever=object(),
        )


def test_lifespan_loads_both_once_and_releases(monkeypatch):
    from app.core.lifespan import lifespan

    calls = []

    def disease():
        calls.append("disease")
        return object()

    def classifier():
        calls.append("crop")
        return object()

    monkeypatch.setattr("app.cv.loader.load_model", disease)
    monkeypatch.setattr("app.cv.crop_classifier.load_crop_classifier", classifier)
    app = FastAPI(lifespan=lifespan)
    with TestClient(app):
        assert app.state.plant_model is not None
        assert app.state.crop_classifier is not None
        assert calls == ["disease", "crop"]
    assert app.state.plant_model is None and app.state.crop_classifier is None
