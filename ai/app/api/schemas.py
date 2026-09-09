from pydantic import BaseModel, Field


from typing import Optional

class DiseaseDetectionResponse(BaseModel):
    class_id: int = Field(..., description="ID của bệnh theo model")
    class_name: str = Field(..., description="Tên bệnh nguyên bản từ model")
    disease_code: Optional[str] = Field(None, description="Mã bệnh")
    disease_name_vi: Optional[str] = Field(None, description="Tên bệnh bằng tiếng Việt")
    confidence: float = Field(..., description="Độ tin cậy của mô hình (0.0 - 1.0)")
    model_name: str = Field(..., description="Tên mô hình được sử dụng")
    model_version: str = Field(..., description="Phiên bản của mô hình")
