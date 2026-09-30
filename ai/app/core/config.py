# SPDX-License-Identifier: Apache-2.0

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

    database_url: str | None = None
    rag_ingest_database_url: str | None = None
    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "qwen2.5:1.5b"
    embedding_model: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"

    cv_weights_path: Path = AI_ROOT / "weights" / "best.pt"
    cv_weights_sha256: str | None = (
        "573db0871f39396f2bbec5e6aa5988d36b79b13e01cb00f03eee29b32a12c60b"
    )
    cv_class_contract_path: Path = (
        AI_ROOT.parent / "data" / "structured" / "class_contract.json"
    )
    cv_num_classes: int = 5
    cv_image_size: int = 640
    cv_model_name: str = "YOLOv8n-RiceLeafBD"
    cv_model_version: str = "3.0.0"
    cv_device: str = "auto"
    cv_detection_confidence_threshold: float = Field(default=0.25, ge=0, le=1)
    cv_iou_threshold: float = Field(default=0.45, ge=0, le=1)
    # Workflow threshold used after CV inference; separate from YOLO box filtering.
    cv_confidence_threshold: float = 0.70
    llm_timeout_seconds: float = 5.0

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
