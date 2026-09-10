from pydantic import BaseModel, Field


class DiseaseDetectionResponse(BaseModel):
    disease_code: str = Field(..., description="Mã bệnh (khớp với Disease.code trong DB)")
    disease_name: str = Field(..., description="Tên bệnh bằng tiếng Việt")
    confidence: float = Field(..., description="Độ tin cậy của mô hình (0.0 - 1.0)")
    model_name: str = Field(..., description="Tên mô hình được sử dụng")
    model_version: str = Field(..., description="Phiên bản của mô hình")

class CVHealthResponse(BaseModel):
    status: str = Field(..., description="Health status, e.g., 'ok'")
    model_loaded: bool = Field(..., description="Whether the CV model is loaded")
    model_name: str | None = Field(None, description="Name of the loaded model")
    model_version: str | None = Field(None, description="Version of the loaded model")
    device: str | None = Field(None, description="Device on which the model runs, e.g., 'cuda' or 'cpu'")

class CVErrorResponse(BaseModel):
    error_code: str = Field(..., description="Machine-friendly error identifier")
    message: str = Field(..., description="Human readable error message")
