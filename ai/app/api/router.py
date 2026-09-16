from typing import Annotated

from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile
from sqlalchemy.orm import Session
from starlette.concurrency import run_in_threadpool

from app.api.schemas import (
    CVHealthResponse,
    DiseaseDetectionResponse,
    RetrieveRequest,
)
from app.core.config import get_settings
from app.cv.contract import class_contract
from app.db.session import get_db
from app.rag.retriever import RAGRetriever
from app.services.plant_disease_service import PlantDiseaseService

router = APIRouter(prefix="/disease", tags=["Disease Detection"])
MAX_IMAGE_BYTES = 10 * 1024 * 1024


async def read_image(image: UploadFile) -> bytes:
    if not (image.content_type or "").startswith("image/"):
        raise HTTPException(400, "File uploaded must be an image.")
    data = await image.read(MAX_IMAGE_BYTES + 1)
    if len(data) > MAX_IMAGE_BYTES:
        raise HTTPException(413, "Image exceeds 10 MiB.")
    return data


def predictor(request: Request) -> PlantDiseaseService:
    model = getattr(request.app.state, "plant_model", None)
    if model is None:
        raise HTTPException(503, "Plant disease model is not available.")
    return PlantDiseaseService(model=model)


@router.get("/health", response_model=CVHealthResponse)
def health(request: Request):
    settings = get_settings()
    model = getattr(request.app.state, "plant_model", None)
    device = getattr(settings, "_chosen_device", None)
    return CVHealthResponse(
        status="ok" if model is not None else "not_ready",
        model_loaded=model is not None,
        model_name=settings.cv_model_name,
        model_version=settings.cv_model_version,
        device=str(device) if device else None,
    )


@router.get("/class-contract")
def get_class_contract():
    return class_contract()


@router.post("/predict", response_model=DiseaseDetectionResponse)
async def predict_disease(request: Request, image: Annotated[UploadFile, File()]):
    data = await read_image(image)
    try:
        return await run_in_threadpool(predictor(request).predict, data)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@router.post("/knowledge/retrieve")
def retrieve(payload: RetrieveRequest, db: Annotated[Session, Depends(get_db)]):
    if not payload.query.strip():
        raise HTTPException(422, "query must not be blank")
    return RAGRetriever().retrieve(
        db, payload.query, top_k=payload.top_k, disease_code=payload.disease_code
    )

