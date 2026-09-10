from typing import Annotated

from fastapi import APIRouter, File, HTTPException, Request, UploadFile

from app.api.schemas import DiseaseDetectionResponse
from app.services.plant_disease_service import PlantDiseaseService

router = APIRouter(prefix="/disease", tags=["Disease Detection"])


@router.post("/predict", response_model=DiseaseDetectionResponse)
async def predict_disease(
    request: Request,
    image: Annotated[UploadFile, File(...)],
) -> dict:
    """Classify a plant image with the model loaded by the app lifespan."""

    content_type = image.content_type or ""
    if not content_type.startswith("image/"):
        raise HTTPException(
            status_code=400,
            detail="File uploaded must be an image.",
        )

    model = getattr(request.app.state, "plant_model", None)
    if model is None:
        raise HTTPException(
            status_code=503,
            detail="Plant disease model is not available.",
        )

    try:
        image_bytes = await image.read()
        return PlantDiseaseService(model=model).predict(image_bytes)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
