# SPDX-License-Identifier: Apache-2.0

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Load both CV models once and release it when FastAPI shuts down."""

    # Keep Ultralytics lazy so non-CV tooling can import the application package.
    from app.cv.crop_classifier import load_crop_classifier
    from app.cv.loader import load_model

    logger.info("Loading plant disease model")
    model = load_model()
    crop_classifier = load_crop_classifier()
    app.state.plant_model = model
    app.state.crop_classifier = crop_classifier
    logger.info("Disease detector and crop classifier loaded")

    try:
        yield
    finally:
        app.state.plant_model = None
        app.state.crop_classifier = None
        logger.info("Plant disease model released")
