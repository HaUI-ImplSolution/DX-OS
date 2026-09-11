# SPDX-License-Identifier: Apache-2.0
import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session
from starlette.concurrency import run_in_threadpool

from app.api.schemas import (
    ApplyWorkflowRequest,
    ApproveWorkflowRequest,
    CVHealthResponse,
    DiseaseDetectionResponse,
    RejectWorkflowRequest,
    RetrieveRequest,
    WorkflowRunResponse,
)
from app.application.workflow_runner import WorkflowRunner
from app.application.workflow_transitions import WorkflowTransitions
from app.core.config import get_settings
from app.cv.contract import class_contract
from app.db.session import get_db
from app.rag.retriever import RAGRetriever
from app.services.diagnosis_service import diagnose_report
from app.services.plant_disease_service import PlantDiseaseService
from app.services.workflow_service import (
    WorkflowConflictError,
)

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
    requested_by: Annotated[str | None, Form()] = None,
):
    data = await read_image(image)
    req_id = request_id or str(uuid.uuid4())
    try:
        result = await run_in_threadpool(
            WorkflowRunner(db, vision_factory=lambda: predictor(request)).run,
            image_bytes=data,
            request_id=req_id,
            parcel_code=parcel_code,
            crop_season_id=crop_season_id,
            rag_question=rag_question,
            requested_by=requested_by,
        )
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    except LookupError as exc:
        raise HTTPException(404, str(exc)) from exc
    except SQLAlchemyError as exc:
        db.rollback()
        raise HTTPException(503, "Workflow database is unavailable") from exc
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(500, "Workflow execution failed") from exc
    return WorkflowRunResponse(**result)


def _transition_error(exc: Exception) -> HTTPException:
    if isinstance(exc, LookupError):
        return HTTPException(404, str(exc))
    if isinstance(exc, WorkflowConflictError):
        return HTTPException(409, str(exc))
    if isinstance(exc, ValueError):
        return HTTPException(422, str(exc))
    if isinstance(exc, SQLAlchemyError):
        return HTTPException(503, "Workflow database is unavailable")
    return HTTPException(500, "Workflow transition failed")


@router.get("/workflow/{case_id}")
def workflow_status(case_id: uuid.UUID, db: Annotated[Session, Depends(get_db)]):
    try:
        return WorkflowTransitions(db).get(case_id)
    except Exception as exc:
        raise _transition_error(exc) from exc


@router.post("/workflow/{case_id}/approve")
def approve_workflow(
    case_id: uuid.UUID,
    payload: ApproveWorkflowRequest,
    db: Annotated[Session, Depends(get_db)],
):
    try:
        return WorkflowTransitions(db).approve(
            case_id,
            actor_id=payload.actor_id,
            selected_rule_id=payload.selected_rule_id,
            approved_dose=payload.approved_dose,
            confirmed_low_confidence=payload.confirmed_low_confidence,
        )
    except Exception as exc:
        raise _transition_error(exc) from exc


@router.post("/workflow/{case_id}/reject")
def reject_workflow(
    case_id: uuid.UUID,
    payload: RejectWorkflowRequest,
    db: Annotated[Session, Depends(get_db)],
):
    try:
        return WorkflowTransitions(db).reject(
            case_id, actor_id=payload.actor_id, reason=payload.reason
        )
    except Exception as exc:
        raise _transition_error(exc) from exc


@router.post("/workflow/{case_id}/applied")
def confirm_applied(
    case_id: uuid.UUID,
    payload: ApplyWorkflowRequest,
    db: Annotated[Session, Depends(get_db)],
):
    try:
        return WorkflowTransitions(db).apply(
            case_id,
            actor_id=payload.actor_id,
            actual_dose=payload.actual_dose,
            applied_at=payload.applied_at,
        )
    except Exception as exc:
        raise _transition_error(exc) from exc
