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
    ingest_database_url: str | None = None
    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "qwen2.5:1.5b"
    embedding_model: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"

    hf_repo_id: str = "Khawajaa/plant-disease-detector"
    weights_filename: str = "best_model.pth"
    cv_num_classes: int = 38
    cv_image_size: int = 224
    cv_model_name: str = "EfficientNet-B4"
    cv_model_version: str = "v1.0"
    cv_device: str = "auto"
    cv_confidence_threshold: float = 0.70
    llm_timeout_seconds: float = 5.0

    log_level: str = "INFO"

    @property
    def weights_dir(self) -> Path:
        return AI_ROOT / "weights"

    @property
    def weights_path(self) -> Path:
        return self.weights_dir / self.weights_filename


@lru_cache
def get_settings() -> Settings:
    return Settings()
