# SPDX-License-Identifier: AGPL-3.0-only
"""Run with ``python -m scripts.check_backend_client`` from ``ai/``."""

from typing import ClassVar
from unittest.mock import patch

import httpx

from app.clients import BackendClient, BackendClientError


class _Response:
    def __init__(self, status_code: int = 200) -> None:
        self.status_code = status_code

    def raise_for_status(self) -> None:
        if self.status_code >= 400:
            request = httpx.Request("GET", "http://backend:8081")
            response = httpx.Response(self.status_code, request=request)
            raise httpx.HTTPStatusError(
                "Backend error", request=request, response=response
            )

    def json(self) -> dict:
        return {
            "request_id": "req-1",
            "disease_code": "Rice___Blast",
            "disease_name": "Bệnh đạo ôn trên lúa",
            "rules": [
                {
                    "rule_id": "7",
                    "pesticide": "Copper 50WP",
                    "dose_min": 1.0,
                    "dose_max": 1.5,
                    "dose_unit": "kg/ha",
                    "phi_days": 7,
                    "version": "v1",
                }
            ],
            "recent_treatments": [],
        }


class _HttpClient:
    outcomes: ClassVar[list[object]] = []
    calls: ClassVar[list[dict]] = []

    def __init__(self, timeout: float):
        assert timeout == 3

    def __enter__(self):
        return self

    def __exit__(self, *_args) -> None:
        pass

    def get(self, url: str, *, params: dict, headers: dict) -> _Response:
        self.__class__.calls.append({"url": url, "params": params, "headers": headers})
        outcome = self.__class__.outcomes.pop(0)
        if isinstance(outcome, Exception):
            raise outcome
        return outcome


def _call(client: BackendClient):
    return client.get_diagnosis_context(
        request_id="req-1",
        disease_code="Rice___Blast",
        crop_season_id=101,
    )


def main() -> None:
    client = BackendClient(
        base_url="http://backend:8081/",
        service_api_key="secret",
        timeout_seconds=3,
        max_attempts=2,
        retry_backoff_seconds=0.2,
    )
    request = httpx.Request("GET", "http://backend:8081")
    _HttpClient.outcomes = [
        httpx.ReadTimeout("temporary timeout", request=request),
        _Response(),
    ]
    _HttpClient.calls = []
    with (
        patch("app.clients.backend_client.httpx.Client", _HttpClient),
        patch("app.clients.backend_client.time.sleep") as sleep,
    ):
        result = _call(client)

    assert result.rules[0].dose_min == 1.0
    assert result.rules[0].dose_max == 1.5
    assert len(_HttpClient.calls) == 2
    sleep.assert_called_once_with(0.2)
    assert _HttpClient.calls[-1] == {
        "url": "http://backend:8081/internal/v1/diagnosis-context",
        "params": {
            "request_id": "req-1",
            "disease_code": "Rice___Blast",
            "crop_season_id": 101,
        },
        "headers": {"Accept": "application/json", "X-Service-Api-Key": "secret"},
    }

    _HttpClient.outcomes = [_Response(503), _Response()]
    _HttpClient.calls = []
    with (
        patch("app.clients.backend_client.httpx.Client", _HttpClient),
        patch("app.clients.backend_client.time.sleep"),
    ):
        _call(client)
    assert len(_HttpClient.calls) == 2

    _HttpClient.outcomes = [_Response(401)]
    _HttpClient.calls = []
    try:
        with patch("app.clients.backend_client.httpx.Client", _HttpClient):
            _call(client)
    except BackendClientError as exc:
        assert "HTTP 401" in str(exc)
    else:
        raise BackendClientError("HTTP 401 was retried or accepted")
    assert len(_HttpClient.calls) == 1

    _HttpClient.outcomes = [
        httpx.ConnectError("temporary connection error", request=request),
        httpx.ConnectError("temporary connection error", request=request),
    ]
    _HttpClient.calls = []
    try:
        with (
            patch("app.clients.backend_client.httpx.Client", _HttpClient),
            patch("app.clients.backend_client.time.sleep"),
        ):
            _call(client)
    except BackendClientError as exc:
        assert "unreachable" in str(exc)
    else:
        raise BackendClientError("Exhausted retries did not fail")
    assert len(_HttpClient.calls) == 2

    try:
        client.get_diagnosis_context(
            request_id="req-1", disease_code="Rice___Blast", crop_season_id=0
        )
    except ValueError:
        pass
    else:
        raise BackendClientError("Invalid crop season was not rejected")

    print("BackendClient contract check: PASS")


if __name__ == "__main__":
    main()
