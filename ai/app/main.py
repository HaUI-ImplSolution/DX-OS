# SPDX-License-Identifier: AGPL-3.0-only

from fastapi import FastAPI

from app.api.router import router
from app.assistant.body_limit import AssistantBodyLimit
from app.assistant.router import router as assistant_router
from app.core.lifespan import lifespan
from app.core.request_id import install_request_id


def create_app() -> FastAPI:
    application = FastAPI(
        title="DX-OS AI Service",
        lifespan=lifespan,
    )
    application.include_router(router, prefix="/api/v1")
    application.include_router(assistant_router, prefix="/api/v1")
    application.add_middleware(AssistantBodyLimit)
    install_request_id(application)
    return application


app = create_app()
