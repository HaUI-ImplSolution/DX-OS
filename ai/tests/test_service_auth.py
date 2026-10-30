# SPDX-License-Identifier: AGPL-3.0-only
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api.router import authorize_backend, inference_router, router
from app.core.config import Settings, get_settings

API_KEY = "test-service-key"
PNG_BYTES = b"\x89PNG\r\n\x1a\n" + b"\x00" * 16


def build_client(api_key: str | None = API_KEY) -> TestClient:
    app = FastAPI()
    app.include_router(router, prefix="/api/v1")
    app.include_router(inference_router, prefix="/api/v1")
    app.dependency_overrides[get_settings] = lambda: Settings(
        ai_service_api_key=api_key, _env_file=None
    )
    return TestClient(app)


def post_predict(client: TestClient, headers: dict | None = None):
    return client.post(
        "/api/v1/disease/predict",
        files={"image": ("leaf.png", PNG_BYTES, "image/png")},
        headers=headers or {},
    )


def post_retrieve(client: TestClient, headers: dict | None = None):
    return client.post(
        "/api/v1/disease/knowledge/retrieve",
        json={"query": "đạo ôn"},
        headers=headers or {},
    )


def test_predict_without_key_is_unauthorized():
    assert post_predict(build_client()).status_code == 401


def test_predict_with_wrong_key_is_unauthorized():
    response = post_predict(build_client(), {"X-Service-Api-Key": "wrong"})
    assert response.status_code == 401


def test_retrieve_without_key_is_unauthorized():
    assert post_retrieve(build_client()).status_code == 401


def test_retrieve_with_wrong_key_is_unauthorized():
    response = post_retrieve(build_client(), {"X-Service-Api-Key": "wrong"})
    assert response.status_code == 401


def test_endpoints_fail_closed_when_key_is_not_configured():
    client = build_client(api_key=None)
    assert post_predict(client, {"X-Service-Api-Key": API_KEY}).status_code == 503
    assert post_retrieve(client, {"X-Service-Api-Key": API_KEY}).status_code == 503


def test_health_and_class_contract_stay_public():
    client = build_client()
    assert client.get("/api/v1/disease/health").status_code == 200
    assert client.get("/api/v1/disease/class-contract").status_code == 200


def test_valid_key_passes_authorization():
    assert authorize_backend(Settings(ai_service_api_key=API_KEY, _env_file=None), API_KEY) is None
