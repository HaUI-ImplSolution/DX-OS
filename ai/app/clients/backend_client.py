# SPDX-License-Identifier: Apache-2.0
"""Read-only client for Backend's internal diagnosis-context API."""

import logging
from datetime import date
from decimal import Decimal

import httpx
from pydantic import BaseModel, Field, ValidationError

from app.core.config import Settings, get_settings

logger = logging.getLogger(__name__)


class RuleFact(BaseModel):
    rule_id: str = Field(min_length=1)
    pesticide: str = Field(min_length=1)
    dose_amount: Decimal = Field(ge=0)
    dose_unit: str = Field(min_length=1)
    phi_days: int = Field(ge=0)
    version: str = Field(min_length=1)


class RecentTreatment(BaseModel):
    pesticide_name: str = Field(min_length=1)
    applied_at: date
    harvest_locked_until: date | None = None


class DiagnosisContext(BaseModel):
    request_id: str = Field(min_length=1)
    disease_code: str = Field(min_length=1)
    disease_name: str = Field(min_length=1)
    rules: list[RuleFact] = Field(min_length=1)
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

        self.base_url = resolved_base_url.strip().rstrip("/")
        self.service_api_key = (resolved_api_key or "").strip()
        self.timeout_seconds = resolved_timeout

        if not self.base_url:
            raise ValueError("backend_base_url must not be blank")
        if not self.service_api_key:
            raise ValueError("backend_service_api_key must not be blank")
        if self.timeout_seconds <= 0:
            raise ValueError("backend_timeout_seconds must be greater than zero")

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
                response = client.get(
                    url,
                    params={
                        "request_id": request_id,
                        "disease_code": disease_code,
                        "crop_season_id": crop_season_id,
                    },
                    headers={
                        "Accept": "application/json",
                        "X-Service-Api-Key": self.service_api_key,
                    },
                )
                response.raise_for_status()
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
