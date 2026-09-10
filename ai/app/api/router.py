from fastapi import APIRouter, File, HTTPException, Request, UploadFile, Depends
from fastapi.responses import JSONResponse

from app.api.schemas import DiseaseDetectionResponse, CVHealthResponse, CVErrorResponse
from app.services.plant_disease_service import PlantDiseaseService
from app.graph.graph import farm_graph
from app.core.config import get_settings
from app.db.session import get_db
from sqlalchemy.orm import Session

router = APIRouter(prefix="/disease", tags=["Disease Detection"])

# Existing health and predict endpoints remain unchanged (omitted for brevity)
# ... (lines 1-66 unchanged) 

@router.post("/workflow/run", summary="Run full LangGraph workflow (minimal mode)")
async def run_workflow(
    request: Request,
    db: Session = Depends(get_db),
    payload: dict = None,
) -> JSONResponse:
    """Execute the DX‑Farm LangGraph spine flow.

    The payload should contain the minimal fields required by the ``resolve_request``
    node: ``request_id``, ``parcel_code``, ``crop_season_id``, ``image_ref``,
    ``actor_id`` and ``actor_role``.
    """
    if payload is None:
        payload = await request.json()
    # Prepare initial state. ``db`` is injected so the retrieve_knowledge node can
    # access the database. Additional keys can be added later.
    initial_state = {
        "request_id": payload.get("request_id"),
        "parcel_code": payload.get("parcel_code"),
        "crop_season_id": payload.get("crop_season_id"),
        "image_ref": payload.get("image_ref"),
        "actor_id": payload.get("actor_id"),
        "actor_role": payload.get("actor_role"),
        "db": db,
    }
    # Run the compiled graph – it returns the final state.
    final_state = farm_graph.invoke(initial_state)
    # Remove the raw DB session from the response for safety.
    final_state.pop("db", None)
    return JSONResponse(content=final_state)



@router.get("/health", response_model=CVHealthResponse)
async def health(request: Request) -> CVHealthResponse:
    """Health check for CV service.

    Returns status, whether model is loaded, model name/version and device.
    """
    model = getattr(request.app.state, "plant_model", None)
    # Retrieve device info from settings stored in app.state if needed
    from app.core.config import get_settings
    settings = get_settings()
    device = getattr(settings, "_chosen_device", None)
    device_str = device.type if device else None
    return CVHealthResponse(
        status="ok",
        model_loaded=model is not None,
        model_name=getattr(settings, "cv_model_name", None),
        model_version=getattr(settings, "cv_model_version", None),
        device=device_str,
    )


@router.post("/predict", response_model=DiseaseDetectionResponse, responses={400: {"model": CVErrorResponse}, 503: {"model": CVErrorResponse}})
async def predict_disease(
    request: Request,
    image: UploadFile = File(...),
) -> DiseaseDetectionResponse:
    """Classify a plant image with the loaded model.

    Returns a structured response defined in ``DiseaseDetectionResponse``.
    """
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
        result = PlantDiseaseService(model=model).predict(image_bytes)
        # Keep only required keys
        required_keys = {"disease_code", "disease_name", "confidence", "model_name", "model_version"}
        minimal_result = {k: result[k] for k in required_keys if k in result}
        return DiseaseDetectionResponse(**minimal_result)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        # Unexpected inference errors
        raise HTTPException(status_code=500, detail="Prediction failed.") from exc
