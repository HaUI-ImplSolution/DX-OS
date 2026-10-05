# SPDX-License-Identifier: Apache-2.0

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Load the CV model once and release it when FastAPI shuts down."""

    # Keep Ultralytics lazy so non-CV tooling can import the application package.
    from app.cv.loader import load_model

    logger.info("Loading plant disease model")
    app.state.plant_model = load_model()
    logger.info("Plant disease model loaded")

    try:
        yield
    finally:
        app.state.plant_model = None
        logger.info("Plant disease model released")
