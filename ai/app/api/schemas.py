from pydantic import BaseModel, Field


class DiseaseDetectionResponse(BaseModel):
    class_id: int = Field(..., description="ID của bệnh theo model")
    class_name: str = Field(..., description="Tên bệnh nguyên bản từ model")
    disease_code: str = Field(..., description="Mã bệnh (khớp với Disease.code trong DB)")
    disease_name: str | None = Field(
        None,
        description="Tên bệnh bằng tiếng Việt",
    )
    confidence: float = Field(
        ...,
        description="Độ tin cậy của mô hình (0.0 - 1.0)",
    )
    model_name: str = Field(..., description="Tên mô hình được sử dụng")
    model_version: str = Field(..., description="Phiên bản của mô hình")
