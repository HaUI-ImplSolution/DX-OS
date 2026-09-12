from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field


class DiseaseDetectionResponse(BaseModel):
    disease_code: str = Field(
        ..., description="Mã bệnh (khớp với Disease.code trong DB)"
    )
    disease_name: str | None = Field("", description="Tên bệnh bằng tiếng Việt")
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


class DiagnoseAndRetrieveResponse(BaseModel):
    cv_result: DiseaseDetectionResponse = Field(
        ..., description="Kết quả nhận diện bệnh từ mô hình CV"
    )
    treatment_knowledge: dict[str, Any] = Field(
        ..., description="Thông tin thuốc được phép / quy trình điều trị / chính sách định hình"
    )
    rag_knowledge: list[dict[str, Any]] = Field(
        ..., description="Danh sách tài liệu RAG giải thích liên quan từ pgvector"
    )


class WorkflowRunRequest(BaseModel):
    request_id: str | None = Field(default=None, description="Mã định danh yêu cầu")
    parcel_code: str | None = Field(default=None, description="Mã mảnh ruộng/lô đất")
    crop_season_id: str | None = Field(default=None, description="ID vụ mùa")
    rag_question: str | None = Field(
        default=None, description="Câu hỏi bổ sung cho RAG"
    )
    requested_by: str | None = Field(default=None, max_length=100)


class WorkflowRunResponse(BaseModel):
    case_id: str | None = None
    request_id: str
    approval_status: str
    application_status: str | None = None
    cv_result: dict[str, Any] | None = None
    field_context: dict[str, Any] | None = None
    treatment_rule: dict[str, Any] | None = None
    rag_context: list[dict[str, Any]] | None = None
    recommendation: Any | None = None
    response: dict[str, Any] | None = None
    errors: list[str] | None = None
    timings_ms: dict[str, float] | None = None


class ApproveWorkflowRequest(BaseModel):
    actor_id: str = Field(min_length=1, max_length=100)
    selected_rule_id: uuid.UUID | None = None
    approved_dose: float | None = None
    confirmed_low_confidence: bool = False


class RejectWorkflowRequest(BaseModel):
    actor_id: str = Field(min_length=1, max_length=100)
    reason: str = Field(min_length=1, max_length=2000)


class ApplyWorkflowRequest(BaseModel):
    actor_id: str = Field(min_length=1, max_length=100)
    actual_dose: float = Field(gt=0)
    applied_at: datetime
