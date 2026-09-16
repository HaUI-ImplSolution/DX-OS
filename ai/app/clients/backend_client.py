# SPDX-License-Identifier: Apache-2.0
"""Read-only REST client for AI Service to fetch operational & structured facts from Backend internal API."""

import logging
from typing import Any

import httpx

from app.core.config import Settings, get_settings

logger = logging.getLogger(__name__)


class BackendClient:
    """Read-only HTTP client to fetch field context, treatment rules, policies, and PHI facts from Backend."""

    def __init__(
        self,
        base_url: str | None = None,
        service_token: str | None = None,
        timeout: float | None = None,
        settings: Settings | None = None,
    ):
        settings = settings or get_settings()
        self.base_url = (base_url or settings.backend_base_url).rstrip("/")
        self.service_token = service_token or settings.backend_internal_service_token
        self.timeout = timeout or settings.backend_timeout_seconds

    def _headers(self, request_id: str | None = None) -> dict[str, str]:
        headers = {"Accept": "application/json"}
        if self.service_token:
            headers["Authorization"] = f"Bearer {self.service_token}"
        if request_id:
            headers["X-Request-ID"] = request_id
        return headers

    def _get(
        self, path: str, params: dict[str, Any] | None = None, request_id: str | None = None
    ) -> dict[str, Any]:
        """Perform a read-only GET request to Backend internal API."""
        url = f"{self.base_url}{path}"
        headers = self._headers(request_id=request_id)
        filtered_params = {k: v for k, v in (params or {}).items() if v is not None}

        try:
            with httpx.Client(timeout=self.timeout) as client:
                response = client.get(url, params=filtered_params, headers=headers)
                response.raise_for_status()
                return response.json()
        except httpx.HTTPStatusError as exc:
            logger.error(
                "Backend GET %s failed status=%s body=%s request_id=%s",
                url,
                exc.response.status_code,
                exc.response.text,
                request_id,
            )
            raise RuntimeError(
                f"Backend internal API error ({exc.response.status_code}): {exc.response.text}"
            ) from exc
        except httpx.RequestError as exc:
            logger.error("Backend GET %s request error: %s request_id=%s", url, exc, request_id)
            raise RuntimeError(f"Backend internal API unreachable: {exc}") from exc

    def get_field_context(
        self,
        crop_season_id: str | None = None,
        parcel_code: str | None = None,
        request_id: str | None = None,
    ) -> dict[str, Any]:
        """Fetch field context (parcel metadata, active season, latest diagnosis & treatment history)."""
        params = {"crop_season_id": crop_season_id, "parcel_code": parcel_code}
        return self._get("/api/v1/internal/field-context", params=params, request_id=request_id)

    def get_structured_facts(
        self,
        disease_code: str,
        crop_season_id: str | None = None,
        request_id: str | None = None,
    ) -> dict[str, Any]:
        """Fetch structured facts (treatment rules, management policy, PHI, allowed pesticides) by disease_code."""
        params = {"disease_code": disease_code, "crop_season_id": crop_season_id}
        return self._get("/api/v1/internal/structured-facts", params=params, request_id=request_id)
