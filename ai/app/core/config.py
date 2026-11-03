# SPDX-License-Identifier: AGPL-3.0-only

from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

AI_ROOT = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    """Central configuration for the AI service."""

    model_config = SettingsConfigDict(
        env_file=AI_ROOT / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # The service reads with database_url; ingest writes with rag_ingest_database_url.
    database_url: str | None = None
    rag_ingest_database_url: str | None = None
    embedding_model: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    search_candidate_count: int = Field(default=20, ge=4, le=40)
    # A passage whose cosine similarity to the question is lower is not evidence.
    search_cosine_min_score: float = Field(default=0.40, ge=-1, le=1)
    ai_service_api_key: str | None = None

    log_level: str = "INFO"


@lru_cache
def get_settings() -> Settings:
    return Settings()
