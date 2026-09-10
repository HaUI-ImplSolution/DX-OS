# SPDX-License-Identifier: Apache-2.0
import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from sqlalchemy.orm import Session
from starlette.concurrency import run_in_threadpool

from app.api.schemas import (
    CVHealthResponse,
    DiseaseDetectionResponse,
    RetrieveRequest,
    WorkflowRunResponse,
)
from app.core.config import get_settings
from app.cv.contract import class_contract
from app.db.session import get_db
from app.graph.graph import farm_graph
from app.graph.state import FarmState
from app.rag.retriever import RAGRetriever
from app.services.diagnosis_service import diagnose_report
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


@router.post("/reports/{report_id}/diagnose")
async def diagnose(
    report_id: uuid.UUID,
    request: Request,
    image: Annotated[UploadFile, File()],
    db: Annotated[Session, Depends(get_db)],
):
    data = await read_image(image)
    try:
        return await run_in_threadpool(
            diagnose_report, db, report_id, data, predictor(request)
        )
    except LookupError as exc:
        raise HTTPException(404, str(exc)) from exc
    except FileExistsError as exc:
        raise HTTPException(409, str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@router.post("/knowledge/retrieve")
def retrieve(payload: RetrieveRequest, db: Annotated[Session, Depends(get_db)]):
    if not payload.query.strip():
        raise HTTPException(422, "query must not be blank")
    return RAGRetriever().retrieve(
        db, payload.query, top_k=payload.top_k, disease_code=payload.disease_code
    )



@router.post("/workflow/run", response_model=WorkflowRunResponse)
async def run_workflow(
    request: Request,
    image: Annotated[UploadFile, File()],
    db: Annotated[Session, Depends(get_db)],
    request_id: Annotated[str | None, Form()] = None,
    parcel_code: Annotated[str | None, Form()] = None,
    crop_season_id: Annotated[str | None, Form()] = None,
    rag_question: Annotated[str | None, Form()] = None,
    approval_status: Annotated[str | None, Form()] = "PENDING",
    approval_reason: Annotated[str | None, Form()] = None,
    selected_rule_id: Annotated[str | None, Form()] = None,
    approved_dose: Annotated[float | None, Form()] = None,
):
    data = await read_image(image)

    # Acquire plant model if loaded, or vision tool wrapper
    vision = None
    if getattr(request.app.state, "plant_model", None) is not None:
        vision = PlantDiseaseService(model=request.app.state.plant_model)

    req_id = request_id or str(uuid.uuid4())

    initial_state: FarmState = {
        "request_id": req_id,
        "parcel_code": parcel_code or "",
        "crop_season_id": crop_season_id or "",
        "image_ref": data,
        "vision_tool": vision,
        "db": db,
        "rag_question": rag_question,
        "approval_status": approval_status or "PENDING",
        "approval_reason": approval_reason,
        "selected_rule_id": selected_rule_id,
        "approved_dose": approved_dose,
        "errors": [],
    }

    try:
        final_state = await run_in_threadpool(farm_graph, initial_state)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    except Exception as exc:
        raise HTTPException(500, f"Workflow execution failed: {exc}") from exc

    return WorkflowRunResponse(
        request_id=final_state.get("request_id", req_id),
        approval_status=final_state.get("approval_status", "PENDING"),
        application_status=final_state.get("application_status"),
        cv_result=final_state.get("cv_result"),
        field_context=final_state.get("field_context"),
        treatment_rule=final_state.get("treatment_rule"),
        rag_context=final_state.get("rag_context"),
        recommendation=final_state.get("recommendation"),
        response=final_state.get("response"),
        errors=final_state.get("errors", []),
    )

