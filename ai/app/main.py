# SPDX-License-Identifier: Apache-2.0

from fastapi import FastAPI

from app.api.router import inference_router
from app.api.router import router as api_router
from app.core.lifespan import lifespan
from app.core.request_id import install_request_id


def create_app() -> FastAPI:
    application = FastAPI(
        title="DX-OS AI Service",
        lifespan=lifespan,
    )
    application.include_router(api_router, prefix="/api/v1")
    application.include_router(inference_router, prefix="/api/v1")
    install_request_id(application)
    return application


app = create_app()
