# SPDX-License-Identifier: Apache-2.0

from fastapi import FastAPI

from app.api.router import inference_router
from app.api.router import router as api_router
from app.core.lifespan import lifespan


def create_app() -> FastAPI:
    application = FastAPI(
        title="DX-Farm AI Service",
        lifespan=lifespan,
    )
    application.include_router(api_router, prefix="/api/v1")
    application.include_router(inference_router, prefix="/api/v1")
    return application


app = create_app()
