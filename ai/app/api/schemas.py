# SPDX-License-Identifier: AGPL-3.0-only
from typing import Literal

from pydantic import BaseModel, Field


class KnowledgeSearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=1000)
    namespace: str = Field(default="default", pattern=r"^[a-z][a-z0-9_-]{0,49}$")
    top_k: int = Field(default=4, ge=1, le=20)
    document_type: str | None = Field(default=None, max_length=100)
    code: str | None = Field(default=None, max_length=100)


class KnowledgePassage(BaseModel):
    rank: int
    chunk_id: str
    document_id: str
    document_title: str
    document_version: str | None = None
    document_type: str | None = None
    code: str | None = None
    source_ids: list[str] = Field(default_factory=list)
    chunk_index: int
    page_number: int | None = None
    content: str
    score: float = Field(description="Cosine similarity, not an accuracy probability")
    bm25_score: float
    rrf_score: float


class KnowledgeSearchResponse(BaseModel):
    query: str
    namespace: str
    status: Literal["FOUND", "NO_EVIDENCE"]
    reason_code: Literal[
        "EVIDENCE_FOUND", "NO_EVIDENCE", "LOW_RELEVANCE", "UNKNOWN_SUBJECT"
    ]
    corpus_version: str | None = None
    strategy: str
    passages: list[KnowledgePassage] = Field(default_factory=list)
    timings_ms: dict[str, float] = Field(default_factory=dict)


class HealthResponse(BaseModel):
    status: Literal["ok"]
    embedding_model: str
