# SPDX-License-Identifier: Apache-2.0

from fastapi import FastAPI

from app.api.router import router as api_router
from app.core.lifespan import lifespan


def create_app() -> FastAPI:
    application = FastAPI(
        title="DX-Farm AI Service",
        description=(
            "API chẩn đoán bệnh cây và điều phối workflow xử lý: "
            "CV nhận diện bệnh, RAG cung cấp bằng chứng, kỹ sư phê duyệt, "
            "sau đó xác nhận APPLIED để ghi nhận xử lý và tính thời gian cách ly (PHI)."
        ),
        version="1.0.0",
        lifespan=lifespan,
    )
    application.include_router(api_router, prefix="/api/v1")
    return application


app = create_app()
