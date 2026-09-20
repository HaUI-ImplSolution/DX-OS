from datetime import date, datetime

from pydantic import AnyHttpUrl, BaseModel, Field


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


class DiagnoseRecentTreatment(BaseModel):
    pesticide_name: str = Field(min_length=1)
    applied_at: datetime
    harvest_locked_until: date | None = None


class DiagnoseFieldContext(BaseModel):
    parcel_code: str = Field(min_length=1)
    crop_name: str | None = None
    growth_stage: str | None = None
    recent_treatments: list[DiagnoseRecentTreatment] = Field(default_factory=list)


class DiagnoseRequest(BaseModel):
    request_id: str = Field(min_length=1, max_length=100)
    crop_season_id: int = Field(gt=0)
    image_url: AnyHttpUrl
    field_context: DiagnoseFieldContext


class DiagnoseCvResult(BaseModel):
    disease_code: str = Field(min_length=1)
    disease_name: str = Field(min_length=1)
    confidence: float = Field(ge=0, le=1)
    model_name: str = Field(min_length=1)
    model_version: str = Field(min_length=1)


class DiagnoseRagEvidence(BaseModel):
    content: str = Field(min_length=1)
    source_ids: list[str] = Field(default_factory=list)
    similarity: float


class DiagnoseRuleFact(BaseModel):
    rule_id: str = Field(min_length=1)
    pesticide: str = Field(min_length=1)
    dose_amount: float = Field(ge=0)
    dose_unit: str = Field(min_length=1)
    phi_days: int = Field(ge=0)
    version: str = Field(min_length=1)


class DiagnoseStructuredContext(BaseModel):
    path: str = Field(pattern="^(treatment|review_required)$")
    rules: list[DiagnoseRuleFact] = Field(default_factory=list)


class DiagnoseRecommendation(BaseModel):
    selected_rule_id: str | None = None
    summary: str = Field(min_length=1)
    requires_engineer_approval: bool = True


class DiagnoseResponse(BaseModel):
    request_id: str
    cv_result: DiagnoseCvResult
    rag_evidence: list[DiagnoseRagEvidence]
    structured_context: DiagnoseStructuredContext
    recommendation: DiagnoseRecommendation
