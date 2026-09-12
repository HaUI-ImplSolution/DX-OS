# SPDX-License-Identifier: Apache-2.0
import os
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    database_url: str = os.getenv(
        "DATABASE_URL",
        "postgresql+psycopg://dxfarm:dxfarm@localhost:5432/dxfarm",
    )
    cv_model_name: str = "EfficientNet-B4"
    cv_model_version: str = "v1.0"
    cv_image_size: int = 224
    cv_confidence_threshold: float = 0.70
    api_env: str = os.getenv("API_ENV", "development")

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
