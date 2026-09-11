import uuid
from datetime import datetime, timezone
from typing import TYPE_CHECKING, Any

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base

if TYPE_CHECKING:
    from .crop_season import CropSeason
    from .disease_report import DiseaseReport
    from .treatment_application import TreatmentApplication
    from .treatment_rule import TreatmentRule


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class WorkflowCase(Base):
    """Durable recommendation/approval state; LangGraph state remains ephemeral."""

    __tablename__ = "workflow_cases"
    __table_args__ = (
        CheckConstraint(
            "structured_path IN ('treatment', 'policy', 'review_required')",
            name="ck_workflow_cases_structured_path",
        ),
        CheckConstraint(
            "approval_status IN ('PENDING', 'APPROVED', 'REJECTED')",
            name="ck_workflow_cases_approval_status",
        ),
        CheckConstraint(
            "application_status IN "
            "('NOT_READY', 'WAITING_APPLIED', 'NOT_APPLICABLE', 'APPLIED', 'CANCELLED')",
            name="ck_workflow_cases_application_status",
        ),
        CheckConstraint(
            "approved_dose IS NULL OR approved_dose > 0",
            name="ck_workflow_cases_approved_dose_positive",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    request_id: Mapped[str] = mapped_column(
        String(100), nullable=False, unique=True, index=True
    )
    crop_season_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("crop_seasons.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    disease_report_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("disease_reports.id", ondelete="CASCADE"),
        nullable=False,
        unique=True,
    )
    disease_code: Mapped[str] = mapped_column(String(100), nullable=False, index=True)
    structured_path: Mapped[str] = mapped_column(String(30), nullable=False)
    recommendation: Mapped[dict[str, Any]] = mapped_column(JSONB, nullable=False)
    rag_source_ids: Mapped[list[str]] = mapped_column(
        JSONB, nullable=False, default=list
    )
    low_confidence: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    approval_status: Mapped[str] = mapped_column(
        String(20), nullable=False, default="PENDING"
    )
    application_status: Mapped[str] = mapped_column(
        String(30), nullable=False, default="NOT_READY"
    )
    approval_reason: Mapped[str | None] = mapped_column(Text, nullable=True)
    selected_rule_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("treatment_rules.id"), nullable=True
    )
    approved_dose: Mapped[float | None] = mapped_column(Float, nullable=True)
    application_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("treatment_applications.id"),
        nullable=True,
        unique=True,
    )
    requested_by: Mapped[str | None] = mapped_column(String(100), nullable=True)
    decided_by: Mapped[str | None] = mapped_column(String(100), nullable=True)
    applied_by: Mapped[str | None] = mapped_column(String(100), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utc_now
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utc_now, onupdate=utc_now
    )
    decided_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    applied_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )

    crop_season: Mapped["CropSeason"] = relationship()
    disease_report: Mapped["DiseaseReport"] = relationship()
    selected_rule: Mapped["TreatmentRule | None"] = relationship()
    application: Mapped["TreatmentApplication | None"] = relationship()
    events: Mapped[list["WorkflowEvent"]] = relationship(
        back_populates="workflow_case",
        cascade="all, delete-orphan",
        order_by="WorkflowEvent.created_at",
    )


class WorkflowEvent(Base):
    """Append-only audit history for human and system transitions."""

    __tablename__ = "workflow_events"
    __table_args__ = (
        UniqueConstraint(
            "workflow_case_id",
            "sequence",
            name="uq_workflow_events_case_sequence",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    workflow_case_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("workflow_cases.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    sequence: Mapped[int] = mapped_column(Integer, nullable=False)
    event_type: Mapped[str] = mapped_column(String(50), nullable=False)
    actor_id: Mapped[str | None] = mapped_column(String(100), nullable=True)
    details: Mapped[dict[str, Any]] = mapped_column(JSONB, nullable=False, default=dict)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utc_now
    )

    workflow_case: Mapped["WorkflowCase"] = relationship(back_populates="events")
