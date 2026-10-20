# SPDX-License-Identifier: Apache-2.0

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

AI_ROOT = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    """Central configuration for the AI service."""

    model_config = SettingsConfigDict(
        env_file=AI_ROOT / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    database_url: str | None = None
    rag_ingest_database_url: str | None = None
    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "qwen2.5:1.5b"
    embedding_model: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"

    cv_weights_path: Path = AI_ROOT / "models" / "efficientnetv2m_rice_disease.pth"
    cv_weights_sha256: str | None = None
    cv_class_contract_path: Path = (
        AI_ROOT.parent / "data" / "structured" / "class_contract.json"
    )
    crop_classifier_path: Path = AI_ROOT / "weights" / "crop_classifier.onnx"
    cv_device: str = "auto"

    backend_base_url: str = "http://localhost:8081"
    backend_service_api_key: str | None = None
    backend_timeout_seconds: float = 3.0
    backend_max_attempts: int = 2
    backend_retry_backoff_seconds: float = 0.2
    ai_service_api_key: str | None = None

    log_level: str = "INFO"


@lru_cache
def get_settings() -> Settings:
    return Settings()
