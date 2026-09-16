from typing import Any

from pydantic import BaseModel, Field


class DiseaseDetectionResponse(BaseModel):
    disease_code: str = Field(
        ..., description="Mã bệnh (khớp với Disease.code trong DB)"
    )
    disease_name: str = Field(..., description="Tên bệnh bằng tiếng Việt")
    confidence: float = Field(
        ..., ge=0, le=1, description="Độ tin cậy của mô hình (0.0 - 1.0)"
    )
    model_name: str = Field(..., description="Tên mô hình được sử dụng")
    model_version: str = Field(..., description="Phiên bản của mô hình")


class CVHealthResponse(BaseModel):
    status: str = Field(..., description="Health status, e.g., 'ok'")
    model_loaded: bool = Field(..., description="Whether the CV model is loaded")
    model_name: str | None = Field(None, description="Name of the loaded model")
    model_version: str | None = Field(None, description="Version of the loaded model")
    device: str | None = Field(
        None, description="Device on which the model runs, e.g., 'cuda' or 'cpu'"
    )


class CVErrorResponse(BaseModel):
    error_code: str = Field(..., description="Machine-friendly error identifier")
    message: str = Field(..., description="Human readable error message")


class RetrieveRequest(BaseModel):
    query: str = Field(min_length=1, max_length=4000)
    disease_code: str | None = Field(default=None, max_length=100)
    top_k: int = Field(default=3, ge=1, le=20)


class DiagnoseRequest(BaseModel):
    request_id: str | None = Field(default=None, description="Mã định danh truy vết luồng")
    crop_season_id: str | None = Field(default=None, description="ID vụ mùa")
    parcel_code: str | None = Field(default=None, description="Mã thửa ruộng")
    image_url: str | None = Field(default=None, description="URL ảnh bệnh nếu không gửi file multipart")
    field_context: dict[str, Any] | None = Field(default=None, description="Bối cảnh vận hành từ Backend")
    rag_question: str | None = Field(default=None, description="Câu hỏi bổ sung cho RAG")


class DiagnoseResponse(BaseModel):
    request_id: str = Field(..., description="Mã định danh yêu cầu")
    cv_result: dict[str, Any] = Field(..., description="Kết quả chẩn đoán mô hình CV")
    rag_evidence: list[dict[str, Any]] = Field(default_factory=list, description="Danh sách bằng chứng từ RAG Vector DB")
    structured_context: dict[str, Any] = Field(default_factory=dict, description="Thông tin quy tắc điều trị và chính sách")
    recommendation: dict[str, Any] = Field(default_factory=dict, description="Đề xuất khuyến nghị tổng hợp")


