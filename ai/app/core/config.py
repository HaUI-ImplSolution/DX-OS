# SPDX-License-Identifier: AGPL-3.0-only

from functools import lru_cache
from pathlib import Path
from typing import Literal

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
    # Off on CPUs too slow for Qwen: answers carry only the verbatim evidence.
    answer_generation_enabled: bool = True
    embedding_model: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    chat_retrieval_strategy: Literal["legacy", "bm25", "hybrid", "hybrid_rerank"] = (
        "hybrid_rerank"
    )
    chat_candidate_count: int = Field(default=20, ge=4, le=40)
    chat_reranker_model: str = "cross-encoder/mmarco-mMiniLMv2-L12-H384-v1"
    chat_rerank_min_score: float = Field(default=0.005, ge=0, le=1)
    chat_cosine_min_score: float = Field(default=0.40, ge=-1, le=1)
    chat_rerank_max_length: int = Field(default=256, ge=64, le=512)
    chat_rerank_batch_size: int = Field(default=4, ge=1, le=16)
    chat_rerank_timeout_seconds: float = Field(default=6, gt=0, le=6)
    chat_queue_timeout_seconds: float = Field(default=12, gt=0, le=12)

    cv_weights_path: Path = AI_ROOT / "models" / "efficientnetv2m_rice_disease.pth"
    cv_weights_sha256: str | None = None
    cv_class_contract_path: Path = (
        AI_ROOT.parent / "data" / "structured" / "class_contract.json"
    )
    crop_classifier_path: Path = AI_ROOT / "weights" / "crop_classifier.onnx"
    crop_classifier_sha256: str | None = None
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
