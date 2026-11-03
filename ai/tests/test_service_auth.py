# SPDX-License-Identifier: AGPL-3.0-only
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api.router import authorize_service, router
from app.core.config import Settings, get_settings

API_KEY = "test-service-key"


def build_client(api_key: str | None = API_KEY) -> TestClient:
    app = FastAPI()
    app.include_router(router, prefix="/api/v1")
    app.dependency_overrides[get_settings] = lambda: Settings(
        ai_service_api_key=api_key, _env_file=None
    )
    return TestClient(app)


def post_search(client: TestClient, headers: dict | None = None):
    return client.post(
        "/api/v1/knowledge/search",
        json={"query": "cách đăng ký"},
        headers=headers or {},
    )


def test_search_without_key_is_unauthorized():
    assert post_search(build_client()).status_code == 401


def test_search_with_wrong_key_is_unauthorized():
    response = post_search(build_client(), {"X-Service-Api-Key": "wrong"})
    assert response.status_code == 401


def test_search_fails_closed_when_key_is_not_configured():
    client = build_client(api_key=None)
    assert post_search(client, {"X-Service-Api-Key": API_KEY}).status_code == 503


def test_health_stays_public():
    assert build_client().get("/api/v1/health").status_code == 200


def test_valid_key_passes_authorization():
    assert authorize_service(Settings(ai_service_api_key=API_KEY, _env_file=None), API_KEY) is None
