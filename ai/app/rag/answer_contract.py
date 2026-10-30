# SPDX-License-Identifier: AGPL-3.0-only
"""Bounded content for generated answers, separate from quoted evidence."""

import re
from typing import Annotated, Literal

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    StringConstraints,
    field_validator,
    model_validator,
)

Profile = Literal["farm_qa", "lab_qa", "farm_diagnosis"]
AbstainReason = Literal["insufficient_evidence", "out_of_scope", "requires_authority"]
AnswerReason = Literal[
    "insufficient_evidence",
    "out_of_scope",
    "requires_authority",
    "generation_failed",
    "conflicting_output",
]
ShortText = Annotated[
    str, StringConstraints(strip_whitespace=True, min_length=1, max_length=180)
]
SourceId = Annotated[str, StringConstraints(pattern=r"^[A-Za-z0-9_.:-]{1,128}$")]


class AnswerDraft(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True, strict=True)

    summary: str = Field(max_length=350)
    steps: list[ShortText] = Field(default_factory=list, max_length=3)
    note: str | None = Field(default=None, max_length=220)
    source_ids: list[SourceId] = Field(max_length=16)
    abstain_reason: AbstainReason | None = None

    @field_validator("summary", "note", mode="before")
    @classmethod
    def plain_text(cls, value):
        return (
            value.replace("**", "").replace("__", "").strip()
            if isinstance(value, str)
            else value
        )

    @model_validator(mode="before")
    @classmethod
    def drop_long_steps(cls, data):
        # An over-long step is dropped; the summary stays usable on its own.
        if isinstance(data, dict) and isinstance(data.get("steps"), list):
            data = {
                **data,
                "steps": [
                    step
                    for step in data["steps"]
                    if not isinstance(step, str) or len(step.strip()) <= 180
                ],
            }
        return data

    @model_validator(mode="before")
    @classmethod
    def reflow_complete_sentences(cls, data):
        if (
            isinstance(data, dict)
            and isinstance(data.get("summary"), str)
            and not data.get("note")
        ):
            summary = data["summary"].strip()
            if len(summary) > 350:
                sentences = re.split(r"(?<=[.!?])\s+", summary)
                for split in range(len(sentences) - 1, 0, -1):
                    head, tail = (
                        " ".join(sentences[:split]),
                        " ".join(sentences[split:]),
                    )
                    if len(head) <= 350 and len(tail) <= 220:
                        return {**data, "summary": head, "note": tail}
        return data

    @model_validator(mode="after")
    def unique_content(self):
        if self.abstain_reason:
            self.summary, self.steps, self.note, self.source_ids = "", [], None, []
            return self
        if not any(char.isalnum() for char in self.summary) or not self.source_ids:
            raise ValueError("Generated content requires a summary and sources")
        seen = {" ".join(self.summary.casefold().split())}
        steps = []
        for step in self.steps:
            key = " ".join(step.casefold().split())
            if key not in seen:
                seen.add(key)
                steps.append(step)
        self.steps = steps
        if self.note and " ".join(self.note.casefold().split()) in seen:
            self.note = None
        self.source_ids = list(dict.fromkeys(self.source_ids))
        return self


class GeneratedAnswer(BaseModel):
    version: Literal[1] = 1
    mode: Literal["generated", "extractive", "insufficient_context"]
    summary: str = Field(max_length=350)
    steps: list[ShortText] = Field(default_factory=list, max_length=3)
    note: str | None = Field(default=None, max_length=220)
    source_ids: list[SourceId] = Field(default_factory=list, max_length=16)
    model_name: str | None = None
    prompt_version: str | None = None
    reason: AnswerReason | None = None

    @model_validator(mode="after")
    def generated_content(self):
        if self.mode == "generated" and (
            not self.summary.strip()
            or not self.source_ids
            or self.reason is not None
            or not self.model_name
            or not self.prompt_version
        ):
            raise ValueError("Generated answer requires content, sources and metadata")
        return self


class AnswerEvidence(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    content: str = Field(min_length=1, max_length=1600)
    document_title: str = Field(default="", max_length=200)
    source_ids: list[SourceId] = Field(default_factory=list, max_length=16)


class KnowledgeAnswerRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    question: str = Field(min_length=1, max_length=1000)
    namespace: Literal["farm", "lab"]
    disease_code: str | None = Field(default=None, max_length=100)
    passages: list[AnswerEvidence] = Field(max_length=4)

    @model_validator(mode="after")
    def bounded_context(self):
        if sum(len(p.content) for p in self.passages) > 2000:
            raise ValueError("Evidence exceeds the retrieval context budget")
        return self
