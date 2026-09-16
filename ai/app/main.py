# SPDX-License-Identifier: Apache-2.0

from fastapi import FastAPI

from app.api.router import diagnose as diagnose_endpoint, router as api_router
from app.api.schemas import DiagnoseResponse
from app.core.lifespan import lifespan


def create_app() -> FastAPI:
    application = FastAPI(
        title="DX-Farm AI Service",
        lifespan=lifespan,
    )
    application.include_router(api_router, prefix="/api/v1")
    application.post(
        "/api/v1/diagnose",
        response_model=DiagnoseResponse,
        tags=["Inference"],
        summary="Stateless AI Inference Contract",
    )(diagnose_endpoint)
    return application


app = create_app()

