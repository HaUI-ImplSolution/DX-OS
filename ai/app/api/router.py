# SPDX-License-Identifier: Apache-2.0
import hmac
from typing import Annotated

from fastapi import APIRouter, Depends, File, Header, HTTPException, Request, UploadFile
from sqlalchemy.orm import Session
from starlette.concurrency import run_in_threadpool

from app.api.schemas import (
    CVHealthResponse,
    DiagnoseRequest,
    DiagnoseResponse,
    DiseaseDetectionResponse,
    RetrieveRequest,
)
from app.clients.backend_client import BackendClient, BackendClientError
from app.core.config import Settings, get_settings
from app.cv.contract import class_contract
from app.db.session import get_db
from app.rag.retriever import RAGRetriever
from app.services.inference_service import (
    ALLOWED_IMAGE_CONTENT_TYPES,
    ImageFetchError,
    fetch_image,
    run_stateless_diagnose,
)
from app.services.plant_disease_service import PlantDiseaseService

router = APIRouter(prefix="/disease", tags=["Disease Detection"])
inference_router = APIRouter(tags=["Inference"])
MAX_IMAGE_BYTES = 10 * 1024 * 1024


async def read_image(image: UploadFile) -> bytes:
    content_type = (image.content_type or "").split(";", 1)[0].lower()
    if content_type not in ALLOWED_IMAGE_CONTENT_TYPES:
        raise HTTPException(400, "File uploaded must be a JPEG or PNG image.")
    data = await image.read(MAX_IMAGE_BYTES + 1)
    if not data:
        raise HTTPException(400, "Image file must not be empty.")
    if len(data) > MAX_IMAGE_BYTES:
        raise HTTPException(413, "Image exceeds 10 MiB.")
    return data


def predictor(request: Request) -> PlantDiseaseService:
    model = getattr(request.app.state, "plant_model", None)
    if model is None:
        raise HTTPException(503, "Plant disease model is not available.")
    return PlantDiseaseService(model=model)


def authorize_backend(
    settings: Annotated[Settings, Depends(get_settings)],
    x_service_api_key: Annotated[str | None, Header(alias="X-Service-Api-Key")] = None,
) -> None:
    expected = (settings.ai_service_api_key or "").strip()
    if not expected:
        raise HTTPException(503, "AI service authentication is not configured.")
    if not x_service_api_key or not hmac.compare_digest(x_service_api_key, expected):
        raise HTTPException(401, "Invalid service API key.")


def backend_client(
    settings: Annotated[Settings, Depends(get_settings)],
) -> BackendClient:
    try:
        return BackendClient(settings=settings)
    except ValueError as exc:
        raise HTTPException(503, "Backend client is not configured.") from exc


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


@inference_router.post(
    "/diagnose",
    response_model=DiagnoseResponse,
    summary="Run stateless disease diagnosis",
    dependencies=[Depends(authorize_backend)],
)
async def diagnose(
    payload: DiagnoseRequest,
    request: Request,
    db: Annotated[Session, Depends(get_db)],
    client: Annotated[BackendClient, Depends(backend_client)],
) -> DiagnoseResponse:
    try:
        image_bytes = await fetch_image(str(payload.image_url))
        return await run_in_threadpool(
            run_stateless_diagnose,
            payload=payload,
            image_bytes=image_bytes,
            predictor=predictor(request),
            db=db,
            backend_client=client,
        )
    except ImageFetchError as exc:
        raise HTTPException(422, str(exc)) from exc
    except BackendClientError as exc:
        raise HTTPException(502, "Backend diagnosis context is unavailable.") from exc
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
