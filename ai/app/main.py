from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.db.models.ENB4C_model.download_model import download_model
from app.api.router import router as api_router


@asynccontextmanager
async def lifespan(app: FastAPI):

    print("Starting application...")

    app.state.plant_model = download_model()

    print("Plant disease model loaded successfully.")

    # Cho FastAPI bắt đầu nhận request
    yield


    # Shutdown
    print("Shutting down application...")

    app.state.plant_model = None

    print("Plant disease model released.")


app = FastAPI(
    title="Plant Disease Detection API",
    lifespan=lifespan,
)


app.include_router(api_router, prefix="/api/v1")