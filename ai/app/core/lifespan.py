# SPDX-License-Identifier: AGPL-3.0-only

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Load the embedding model at startup when it is already on disk."""

    # Imported here so tooling can import the application package without PyTorch.
    from app.rag.embedding_service import preload_cached_model

    if preload_cached_model():
        logger.info("Embedding model loaded")
    else:
        logger.warning("Embedding model is not cached; it loads on the first search")
    yield
