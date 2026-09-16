"""Run with ``python -m scripts.check_backend_client`` from ``ai/``."""

from unittest.mock import patch

from app.clients import BackendClient, BackendClientError


class _Response:
    def raise_for_status(self) -> None:
        pass

    def json(self) -> dict:
        return {
            "request_id": "req-1",
            "disease_code": "Tomato___Late_blight",
            "disease_name": "Bệnh mốc sương cà chua",
            "rules": [
                {
                    "rule_id": "7",
                    "pesticide": "Copper 50WP",
                    "dose_amount": 1.5,
                    "dose_unit": "kg/ha",
                    "phi_days": 7,
                    "version": "v1",
                }
            ],
            "recent_treatments": [],
        }


class _HttpClient:
    call: dict | None = None

    def __init__(self, timeout: float):
        assert timeout == 3

    def __enter__(self):
        return self

    def __exit__(self, *_args) -> None:
        pass

    def get(self, url: str, *, params: dict, headers: dict) -> _Response:
        self.__class__.call = {"url": url, "params": params, "headers": headers}
        return _Response()


def main() -> None:
    client = BackendClient(
        base_url="http://backend:8081/",
        service_api_key="secret",
        timeout_seconds=3,
    )
    with patch("app.clients.backend_client.httpx.Client", _HttpClient):
        result = client.get_diagnosis_context(
            request_id="req-1",
            disease_code="Tomato___Late_blight",
            crop_season_id=101,
        )

    assert result.rules[0].dose_amount == 1.5
    assert _HttpClient.call == {
        "url": "http://backend:8081/internal/v1/diagnosis-context",
        "params": {
            "request_id": "req-1",
            "disease_code": "Tomato___Late_blight",
            "crop_season_id": 101,
        },
        "headers": {"Accept": "application/json", "X-Service-Api-Key": "secret"},
    }

    try:
        client.get_diagnosis_context(
            request_id="req-1", disease_code="Tomato___Late_blight", crop_season_id=0
        )
    except ValueError:
        pass
    else:
        raise BackendClientError("Invalid crop season was not rejected")

    print("BackendClient contract check: PASS")


if __name__ == "__main__":
    main()
