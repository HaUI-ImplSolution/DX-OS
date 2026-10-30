# SPDX-License-Identifier: AGPL-3.0-only
"""Bounded local CrossEncoder inference; assets are prepared outside requests."""

from contextlib import asynccontextmanager
import logging

from concurrent.futures import ThreadPoolExecutor, TimeoutError
from functools import lru_cache
from threading import BoundedSemaphore
from time import monotonic

from app.core.config import get_settings
from app.core.torch_cpu import configure_torch_cpu

_executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="chat-rerank")
_slot = BoundedSemaphore(1)
logger = logging.getLogger(__name__)


@lru_cache(maxsize=1)
def _model():
    from sentence_transformers import CrossEncoder

    settings = get_settings()
    configure_torch_cpu()
    return CrossEncoder(
        settings.chat_reranker_model,
        device="cpu",
        max_length=settings.chat_rerank_max_length,
        local_files_only=True,
    )


def prepare_reranker() -> str:
    from pathlib import Path

    from huggingface_hub import snapshot_download
    from huggingface_hub.errors import LocalEntryNotFoundError

    source = get_settings().chat_reranker_model
    if Path(source).is_dir():
        return source
    try:
        cached = Path(snapshot_download(source, local_files_only=True))
        if (cached / "config.json").is_file() and any(cached.glob("*.safetensors")):
            return str(cached)
    except LocalEntryNotFoundError:
        pass
    return snapshot_download(
        get_settings().chat_reranker_model,
        allow_patterns=["*.json", "*.txt", "*.model", "*.safetensors"],
        ignore_patterns=["onnx/*", "openvino/*"],
    )


def rerank(query: str, passages: list[dict]) -> list[float]:
    settings = get_settings()
    deadline = monotonic() + settings.chat_rerank_timeout_seconds
    if not _slot.acquire(timeout=settings.chat_rerank_timeout_seconds):
        raise RuntimeError("Reranker is busy")

    def predict():
        import torch

        try:
            scores = _model().predict(
                [(query, p["document_title"] + "\n" + p["content"]) for p in passages],
                batch_size=settings.chat_rerank_batch_size,
                activation_fn=torch.nn.Sigmoid(),
                show_progress_bar=False,
            )
            return [float(score) for score in scores]
        finally:
            _slot.release()

    try:
        future = _executor.submit(predict)
    except BaseException:
        _slot.release()
        raise
    try:
        return future.result(timeout=max(0.001, deadline - monotonic()))
    except TimeoutError:
        # The slot remains held until the in-flight CPU work really stops.
        raise TimeoutError("Reranker exceeded its time budget") from None


@asynccontextmanager
async def chat_lifespan(app):
    """Load the cached embedding model and ranker before accepting requests; never fetch assets here."""
    from starlette.concurrency import run_in_threadpool

    from app.rag.embedding_service import preload_cached_model

    try:
        if not await run_in_threadpool(preload_cached_model):
            logger.warning("Embedding model is not cached; the first search loads it")
    except (OSError, RuntimeError):
        logger.warning("Embedding model failed to preload; the first search loads it")
    if get_settings().chat_retrieval_strategy == "hybrid_rerank":
        try:
            await run_in_threadpool(_model)
        except (OSError, RuntimeError):
            logger.warning("Chat reranker unavailable; chat retrieval will report RERANK_UNAVAILABLE")
    yield
