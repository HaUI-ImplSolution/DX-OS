# SPDX-License-Identifier: AGPL-3.0-only
import json
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class Contract(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class Document(Contract):
    id: str = Field(min_length=1, max_length=100, pattern=r"^[a-zA-Z0-9:_-]+$")
    text: str = Field(min_length=1, max_length=12000)


class ContextRequest(Contract):
    query: str = Field(default="", max_length=12000)
    documents: list[Document] = Field(max_length=200)
    timeout_seconds: float = Field(default=60, gt=0, le=60, alias="timeoutSeconds")

    @model_validator(mode="after")
    def validate_context(self):
        if len({doc.id for doc in self.documents}) != len(self.documents):
            raise ValueError("Duplicate evidence identifiers")
        if len(json.dumps(self.model_dump(), ensure_ascii=False).encode()) > 128 * 1024:
            raise ValueError("Context exceeds 128 KiB")
        return self


class RankedItem(Contract):
    id: str
    score: float


class RankResponse(Contract):
    items: list[RankedItem]


class SummaryItem(Contract):
    text: str = Field(min_length=1, max_length=600)
    evidence_ids: list[str] = Field(min_length=1, max_length=8, alias="evidenceIds")


class SummaryDraft(Contract):
    summary: list[SummaryItem] = Field(min_length=1, max_length=4)


class SummaryResponse(Contract):
    status: Literal["GENERATED", "DISABLED", "UNAVAILABLE", "INVALID", "CONTEXT_LIMIT"]
    summary: list[SummaryItem] = Field(default_factory=list)


class FieldDefinition(Contract):
    name: str = Field(max_length=80)
    label: str = Field(max_length=200)
    type: str = Field(max_length=20)
    options: list[str] = Field(default_factory=list, max_length=100)
    labels: dict[str, str] = Field(default_factory=dict, max_length=100)


class PlanRequest(Contract):
    query: str = Field(min_length=1, max_length=1000)
    fields: list[FieldDefinition] = Field(max_length=100)
    timeout_seconds: float = Field(default=60, gt=0, le=60, alias="timeoutSeconds")


class PlanResponse(Contract):
    status: Literal["SUPPORTED", "NEEDS_CLARIFICATION"]
    query: str = Field(default="", max_length=1000)
    filters: dict[str, str] = Field(default_factory=dict, max_length=20)


class ProposedFilter(Contract):
    field: str = Field(min_length=1, max_length=80)
    operator: Literal["eq", "ne", "lt", "lte", "gt", "gte", "like", "in"]
    value: str = Field(max_length=1000)


class PlanDraft(Contract):
    status: Literal["SUPPORTED", "NEEDS_CLARIFICATION"]
    query: str = Field(default="", max_length=1000)
    filters: list[ProposedFilter] = Field(default_factory=list, max_length=20)
