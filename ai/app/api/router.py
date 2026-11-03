# SPDX-License-Identifier: AGPL-3.0-only
import hmac
from typing import Annotated

from fastapi import APIRouter, Depends, Header, HTTPException
from sqlalchemy.orm import Session

from app.api.schemas import (
    HealthResponse,
    KnowledgeSearchRequest,
    KnowledgeSearchResponse,
)
from app.core.config import Settings, get_settings
from app.db.session import get_db
from app.rag.search import HybridSearch

router = APIRouter(tags=["Knowledge"])


def authorize_service(
    settings: Annotated[Settings, Depends(get_settings)],
    x_service_api_key: Annotated[str | None, Header(alias="X-Service-Api-Key")] = None,
) -> None:
    expected = (settings.ai_service_api_key or "").strip()
    if not expected:
        raise HTTPException(503, "AI service authentication is not configured.")
    if not x_service_api_key or not hmac.compare_digest(x_service_api_key, expected):
        raise HTTPException(401, "Invalid service API key.")


@router.get("/health", response_model=HealthResponse)
def health(settings: Annotated[Settings, Depends(get_settings)]):
    return HealthResponse(status="ok", embedding_model=settings.embedding_model)


@router.post(
    "/knowledge/search",
    response_model=KnowledgeSearchResponse,
    summary="Search the knowledge base with BM25 and cosine similarity",
    responses={422: {"description": "Invalid query"}},
    dependencies=[Depends(authorize_service)],
)
def search_knowledge(
    payload: KnowledgeSearchRequest, db: Annotated[Session, Depends(get_db)]
):
    try:
        return HybridSearch().search(
            db,
            payload.query,
            payload.namespace,
            payload.top_k,
            payload.document_type,
            payload.code,
        )
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
