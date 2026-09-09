from fastapi import APIRouter, File, HTTPException, Request, UploadFile

from app.api.schemas import DiseaseDetectionResponse
from src import PlantDiseaseService

router = APIRouter(prefix="/disease", tags=["Disease Detection"])


@router.post("/predict", response_model=DiseaseDetectionResponse)
async def predict_disease(request: Request, image: UploadFile = File(...)):
    """
    Nhận vào một ảnh chụp thực vật và trả về kết quả chẩn đoán bệnh.
    """
    if not image.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="File uploaded must be an image.")

    try:
        image_bytes = await image.read()

        # Khởi tạo service với model đã được load từ main.py lifespan
        model = request.app.state.plant_model
        service = PlantDiseaseService(model=model)

        # Thực hiện dự đoán
        result = service.predict(image_bytes)

        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error processing image: {str(e)}")
