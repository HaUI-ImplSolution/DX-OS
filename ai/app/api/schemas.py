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


class WorkflowRunRequest(BaseModel):
    request_id: str | None = Field(default=None, description="Mã định danh yêu cầu")
    parcel_code: str | None = Field(default=None, description="Mã mảnh ruộng/lô đất")
    crop_season_id: str | None = Field(default=None, description="ID vụ mùa")
    rag_question: str | None = Field(default=None, description="Câu hỏi bổ sung cho RAG")
    approval_status: str | None = Field(
        default="PENDING", description="Trạng thái phê duyệt: PENDING, APPROVED, REJECTED"
    )
    approval_reason: str | None = Field(
        default=None, description="Lý do từ chối nếu REJECTED"
    )
    selected_rule_id: str | None = Field(
        default=None, description="ID quy trình được chọn nếu APPROVED"
    )
    approved_dose: float | None = Field(
        default=None, description="Liều lượng phê duyệt"
    )


class WorkflowRunResponse(BaseModel):
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

