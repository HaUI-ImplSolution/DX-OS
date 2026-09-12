# SPDX-License-Identifier: Apache-2.0

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Load the CV model once and release it when FastAPI shuts down."""

    from src.cv.loader import download_model

    logger.info("Loading plant disease model")
    try:
        app.state.plant_model = download_model()
        logger.info("Plant disease model loaded")
    except Exception as exc:
        logger.warning(f"Could not load plant disease model at startup: {exc}")
        app.state.plant_model = None

    try:
        yield
    finally:
        app.state.plant_model = None
        logger.info("Plant disease model released")
