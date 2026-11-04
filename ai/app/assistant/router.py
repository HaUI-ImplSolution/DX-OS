# SPDX-License-Identifier: AGPL-3.0-only
from threading import BoundedSemaphore

from fastapi import APIRouter, Depends, HTTPException
from httpx import HTTPError
from ollama import ResponseError

from app.api.router import authorize_service
from app.assistant.pipeline import AssistantPipeline
from app.assistant.schemas import (
    ContextRequest,
    PlanRequest,
    PlanResponse,
    RankResponse,
    SummaryResponse,
)

router = APIRouter(
    prefix="/assistant", tags=["Assistant"], dependencies=[Depends(authorize_service)]
)
capacity = BoundedSemaphore(1)


def available():
    if not capacity.acquire(blocking=False):
        raise HTTPException(503, "Assistant is busy; try again later")
    try:
        yield
    finally:
        capacity.release()


@router.post("/rank", response_model=RankResponse, dependencies=[Depends(available)])
def rank(request: ContextRequest):
    try:
        return RankResponse(items=AssistantPipeline().rank(request))
    except TimeoutError:
        raise HTTPException(503, "Ranking deadline exceeded") from None
    except (RuntimeError, OSError):
        raise HTTPException(503, "Embedding model is not available") from None


@router.post("/plan", response_model=PlanResponse, dependencies=[Depends(available)])
def plan(request: PlanRequest):
    try:
        return AssistantPipeline().plan(request)
    except (RuntimeError, OSError, HTTPError, ResponseError):
        return PlanResponse(status="NEEDS_CLARIFICATION")


@router.post(
    "/summarize", response_model=SummaryResponse, dependencies=[Depends(available)]
)
def summarize(request: ContextRequest):
    try:
        return AssistantPipeline().summarize(request)
    except (RuntimeError, OSError, HTTPError, ResponseError):
        return SummaryResponse(status="UNAVAILABLE")
