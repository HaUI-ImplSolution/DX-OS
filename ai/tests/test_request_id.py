# SPDX-License-Identifier: AGPL-3.0-only
import logging

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.core.request_id import access_log, install_request_id


def build_client() -> TestClient:
    app = FastAPI()
    install_request_id(app)

    @app.get("/ping")
    def ping():
        return {"ok": True}

    return TestClient(app)


def logged(caplog, text: str) -> bool:
    return any(text in record.getMessage() for record in caplog.records)


def test_request_id_from_the_backend_is_echoed_and_logged(caplog):
    access_log.propagate = True
    with caplog.at_level(logging.INFO, logger="app.access"):
        response = build_client().get("/ping", headers={"X-Request-Id": "req-123.abc"})

    assert response.headers["X-Request-Id"] == "req-123.abc"
    assert logged(caplog, "request_id=req-123.abc GET /ping status=200")


def test_unusable_request_id_is_replaced():
    response = build_client().get("/ping", headers={"X-Request-Id": "bad id\twith spaces"})

    assert response.headers["X-Request-Id"] != "bad id\twith spaces"
    assert len(response.headers["X-Request-Id"]) == 32


def test_request_without_id_gets_one():
    response = build_client().get("/ping")

    assert len(response.headers["X-Request-Id"]) == 32
