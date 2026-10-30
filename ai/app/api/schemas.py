# SPDX-License-Identifier: AGPL-3.0-only
from datetime import date, datetime
from typing import Literal

from pydantic import AnyHttpUrl, BaseModel, Field, model_validator

from app.rag.answer_contract import GeneratedAnswer


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
    model_loaded: bool = Field(
        ..., description="Whether both disease detector and crop classifier are loaded"
    )
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
    # Corpus to search; each app owns one namespace.
    namespace: str = Field(default="farm", min_length=1, max_length=50)
    # Narrows the search to one kind of document; the whole corpus when absent.
    document_type: str | None = Field(default=None, max_length=100)
    disease_code: str | None = Field(default=None, max_length=100)
    top_k: int = Field(default=3, ge=1, le=20)


class KnowledgeSearchRequest(BaseModel):
    model_config = {"extra": "forbid"}
    query: str = Field(min_length=1, max_length=4000)
    namespace: str = Field(min_length=1, max_length=50)
    document_type: str | None = Field(default=None, max_length=100)
    disease_code: str | None = Field(default=None, max_length=100)
    top_k: int = Field(default=4, ge=1, le=20)


class KnowledgeSearchPassage(BaseModel):
    chunk_id: str
    document_id: str
    document_title: str
    document_version: str | None = None
    document_type: str | None = None
    disease_code: str | None = None
    namespace: str
    chunk_index: int
    content: str
    page_number: int | None = None
    source_ids: list[str]
    distance: float
    score: float = Field(description="Cosine similarity, not an accuracy probability")
    similarity: float
    score_kind: Literal["cosine"]
    bm25_score: float | None = None
    rrf_score: float | None = None
    rerank_score: float | None = None
    rank: int | None = None


class KnowledgeSearchResponse(BaseModel):
    question: str
    namespace: str
    scope_status: Literal["OUT_OF_SCOPE", "IN_SCOPE_NO_EVIDENCE", "ANSWERABLE"]
    reason_code: str
    corpus_version: str | None = None
    strategy: str
    passages: list[KnowledgeSearchPassage] = Field(default_factory=list)
    rankings: dict[str, list[KnowledgeSearchPassage]] = Field(default_factory=dict)
    timings_ms: dict[str, float] = Field(default_factory=dict)


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
    include_explanation: bool = False


class DiagnoseCvResult(BaseModel):
    disease_code: str = Field(min_length=1)
    disease_name: str = Field(min_length=1)
    confidence: float = Field(ge=0, le=1)
    model_name: str = Field(min_length=1)
    model_version: str = Field(min_length=1)


class DiagnoseRagEvidence(BaseModel):
    content: str = Field(min_length=1)
    document_title: str | None = None
    chunk_index: int | None = None
    source_ids: list[str] = Field(default_factory=list)
    similarity: float


class DiagnoseRuleFact(BaseModel):
    rule_id: str = Field(min_length=1)
    pesticide: str = Field(min_length=1)
    dose_min: float = Field(ge=0)
    dose_max: float = Field(ge=0)
    dose_unit: str = Field(min_length=1)
    phi_days: int = Field(ge=0)
    version: str = Field(min_length=1)

    @model_validator(mode="after")
    def validate_dose_range(self) -> "DiagnoseRuleFact":
        if self.dose_min > self.dose_max:
            raise ValueError("dose_min must not exceed dose_max")
        return self


class DiagnoseStructuredContext(BaseModel):
    path: str = Field(pattern="^(treatment|review_required)$")
    rules: list[DiagnoseRuleFact] = Field(default_factory=list)


class DiagnoseRecommendation(BaseModel):
    selected_rule_id: str | None = None
    requires_engineer_approval: bool = True


class DiagnoseResponse(BaseModel):
    request_id: str
    cv_result: DiagnoseCvResult
    rag_evidence: list[DiagnoseRagEvidence]
    structured_context: DiagnoseStructuredContext
    recommendation: DiagnoseRecommendation
    generated_explanation: GeneratedAnswer | None = Field(
        default=None, exclude_if=lambda value: value is None
    )


class ImagePrecheckResponse(BaseModel):
    passed: bool = Field(
        description="Passed image quality and rice classification; not a disease diagnosis"
    )
    reasons: list[str]
    width: int
    height: int
    sharpness: float
    brightness: float


class SymptomSuggestRequest(BaseModel):
    symptom_text: str = Field(min_length=1, max_length=4000)
    top_k: int = Field(default=5, ge=1, le=6)


class SymptomSuggestion(BaseModel):
    disease_code: str
    disease_name: str
    score: float
    evidence: str
    source_ids: list[str]
