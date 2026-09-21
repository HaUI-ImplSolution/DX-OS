# SPDX-License-Identifier: Apache-2.0
"""Read-only client for Backend's internal diagnosis-context API."""

import logging
import time
from datetime import date
from decimal import Decimal

import httpx
from pydantic import BaseModel, Field, ValidationError, model_validator

from app.core.config import Settings, get_settings

logger = logging.getLogger(__name__)
RETRYABLE_STATUS_CODES = frozenset({502, 503, 504})


def _get_with_retry(
    *,
    client: httpx.Client,
    url: str,
    params: dict,
    headers: dict,
    max_attempts: int,
    backoff_seconds: float,
    request_id: str,
) -> httpx.Response:
    for attempt in range(1, max_attempts + 1):
        try:
            response = client.get(url, params=params, headers=headers)
            response.raise_for_status()
            return response
        except (httpx.TransportError, httpx.HTTPStatusError) as exc:
            status = (
                exc.response.status_code
                if isinstance(exc, httpx.HTTPStatusError)
                else None
            )
            retryable = isinstance(exc, httpx.TransportError) or (
                status in RETRYABLE_STATUS_CODES
            )
            if not retryable or attempt >= max_attempts:
                raise
            logger.warning(
                "Retrying Backend diagnosis context attempt=%s request_id=%s",
                attempt + 1,
                request_id,
            )
            time.sleep(backoff_seconds)

    raise RuntimeError("Backend diagnosis context exhausted all attempts")


class RuleFact(BaseModel):
    rule_id: str = Field(min_length=1)
    pesticide: str = Field(min_length=1)
    dose_min: Decimal = Field(ge=0)
    dose_max: Decimal = Field(ge=0)
    dose_unit: str = Field(min_length=1)
    phi_days: int = Field(ge=0)
    version: str = Field(min_length=1)

    @model_validator(mode="after")
    def validate_dose_range(self) -> "RuleFact":
        if self.dose_min > self.dose_max:
            raise ValueError("dose_min must not exceed dose_max")
        return self


class RecentTreatment(BaseModel):
    pesticide_name: str = Field(min_length=1)
    applied_at: date
    harvest_locked_until: date | None = None


class DiagnosisContext(BaseModel):
    request_id: str = Field(min_length=1)
    disease_code: str = Field(min_length=1)
    disease_name: str = Field(min_length=1)
    rules: list[RuleFact] = Field(default_factory=list)
    recent_treatments: list[RecentTreatment]


class BackendClientError(RuntimeError):
    """Backend could not provide a valid diagnosis context."""


class BackendClient:
    """Fetch authoritative business facts from Backend without write access."""

    def __init__(
        self,
        base_url: str | None = None,
        service_api_key: str | None = None,
        timeout_seconds: float | None = None,
        max_attempts: int | None = None,
        retry_backoff_seconds: float | None = None,
        settings: Settings | None = None,
    ) -> None:
        settings = settings or get_settings()
        resolved_base_url = (
            base_url if base_url is not None else settings.backend_base_url
        )
        resolved_api_key = (
            service_api_key
            if service_api_key is not None
            else settings.backend_service_api_key
        )
        resolved_timeout = (
            timeout_seconds
            if timeout_seconds is not None
            else settings.backend_timeout_seconds
        )
        resolved_max_attempts = (
            max_attempts if max_attempts is not None else settings.backend_max_attempts
        )
        resolved_retry_backoff = (
            retry_backoff_seconds
            if retry_backoff_seconds is not None
            else settings.backend_retry_backoff_seconds
        )

        self.base_url = resolved_base_url.strip().rstrip("/")
        self.service_api_key = (resolved_api_key or "").strip()
        self.timeout_seconds = resolved_timeout
        self.max_attempts = resolved_max_attempts
        self.retry_backoff_seconds = resolved_retry_backoff

        if not self.base_url:
            raise ValueError("backend_base_url must not be blank")
        if not self.service_api_key:
            raise ValueError("backend_service_api_key must not be blank")
        if self.timeout_seconds <= 0:
            raise ValueError("backend_timeout_seconds must be greater than zero")
        if not 1 <= self.max_attempts <= 3:
            raise ValueError("backend_max_attempts must be between 1 and 3")
        if self.retry_backoff_seconds < 0:
            raise ValueError("backend_retry_backoff_seconds must not be negative")

    def get_diagnosis_context(
        self,
        *,
        request_id: str,
        disease_code: str,
        crop_season_id: int,
    ) -> DiagnosisContext:
        request_id = request_id.strip()
        disease_code = disease_code.strip()
        if not request_id:
            raise ValueError("request_id must not be blank")
        if not disease_code:
            raise ValueError("disease_code must not be blank")
        if crop_season_id <= 0:
            raise ValueError("crop_season_id must be greater than zero")

        url = f"{self.base_url}/internal/v1/diagnosis-context"
        try:
            with httpx.Client(timeout=self.timeout_seconds) as client:
                response = _get_with_retry(
                    client=client,
                    url=url,
                    params={
                        "request_id": request_id,
                        "disease_code": disease_code,
                        "crop_season_id": crop_season_id,
                    },
                    headers={
                        "Accept": "application/json",
                        "X-Service-Api-Key": self.service_api_key,
                    },
                    max_attempts=self.max_attempts,
                    backoff_seconds=self.retry_backoff_seconds,
                    request_id=request_id,
                )
                context = DiagnosisContext.model_validate(response.json())
        except httpx.HTTPStatusError as exc:
            logger.error(
                "Backend diagnosis context failed status=%s request_id=%s",
                exc.response.status_code,
                request_id,
            )
            raise BackendClientError(
                f"Backend diagnosis context returned HTTP {exc.response.status_code}"
            ) from exc
        except httpx.RequestError as exc:
            logger.error(
                "Backend diagnosis context unreachable request_id=%s", request_id
            )
            raise BackendClientError(
                "Backend diagnosis context is unreachable"
            ) from exc
        except (ValueError, ValidationError) as exc:
            logger.error(
                "Backend diagnosis context is invalid request_id=%s", request_id
            )
            raise BackendClientError(
                "Backend returned an invalid diagnosis context"
            ) from exc

        if context.request_id != request_id or context.disease_code != disease_code:
            raise BackendClientError(
                "Backend diagnosis context does not match the request"
            )
        return context
