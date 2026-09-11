"""Stage 9 durable HITL workflow and treatment snapshots.

Revision ID: e8f9a1026011
Revises: d6e7a1202601
Create Date: 2026-09-11
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "e8f9a1026011"
down_revision: str | Sequence[str] | None = "d6e7a1202601"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "treatment_applications",
        sa.Column("pesticide_name", sa.String(length=255), nullable=True),
    )
    op.add_column(
        "treatment_applications",
        sa.Column("active_ingredient", sa.String(length=255), nullable=True),
    )
    op.add_column(
        "treatment_applications",
        sa.Column("rule_version", sa.String(length=50), nullable=True),
    )
    op.add_column(
        "treatment_applications",
        sa.Column("rule_source", sa.String(length=255), nullable=True),
    )

    op.create_table(
        "workflow_cases",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("request_id", sa.String(length=100), nullable=False),
        sa.Column("crop_season_id", sa.UUID(), nullable=False),
        sa.Column("disease_report_id", sa.UUID(), nullable=False),
        sa.Column("disease_code", sa.String(length=100), nullable=False),
        sa.Column("structured_path", sa.String(length=30), nullable=False),
        sa.Column(
            "recommendation", postgresql.JSONB(astext_type=sa.Text()), nullable=False
        ),
        sa.Column(
            "rag_source_ids", postgresql.JSONB(astext_type=sa.Text()), nullable=False
        ),
        sa.Column("low_confidence", sa.Boolean(), nullable=False),
        sa.Column("approval_status", sa.String(length=20), nullable=False),
        sa.Column("application_status", sa.String(length=30), nullable=False),
        sa.Column("approval_reason", sa.Text(), nullable=True),
        sa.Column("selected_rule_id", sa.UUID(), nullable=True),
        sa.Column("approved_dose", sa.Float(), nullable=True),
        sa.Column("application_id", sa.UUID(), nullable=True),
        sa.Column("requested_by", sa.String(length=100), nullable=True),
        sa.Column("decided_by", sa.String(length=100), nullable=True),
        sa.Column("applied_by", sa.String(length=100), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("decided_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("applied_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint(
            "application_status IN "
            "('NOT_READY', 'WAITING_APPLIED', 'NOT_APPLICABLE', 'APPLIED', 'CANCELLED')",
            name="ck_workflow_cases_application_status",
        ),
        sa.CheckConstraint(
            "approval_status IN ('PENDING', 'APPROVED', 'REJECTED')",
            name="ck_workflow_cases_approval_status",
        ),
        sa.CheckConstraint(
            "approved_dose IS NULL OR approved_dose > 0",
            name="ck_workflow_cases_approved_dose_positive",
        ),
        sa.CheckConstraint(
            "structured_path IN ('treatment', 'policy', 'review_required')",
            name="ck_workflow_cases_structured_path",
        ),
        sa.ForeignKeyConstraint(["application_id"], ["treatment_applications.id"]),
        sa.ForeignKeyConstraint(
            ["crop_season_id"], ["crop_seasons.id"], ondelete="CASCADE"
        ),
        sa.ForeignKeyConstraint(
            ["disease_report_id"], ["disease_reports.id"], ondelete="CASCADE"
        ),
        sa.ForeignKeyConstraint(["selected_rule_id"], ["treatment_rules.id"]),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("application_id"),
        sa.UniqueConstraint("disease_report_id"),
    )
    op.create_index(
        op.f("ix_workflow_cases_crop_season_id"),
        "workflow_cases",
        ["crop_season_id"],
        unique=False,
    )
    op.create_index(
        op.f("ix_workflow_cases_disease_code"),
        "workflow_cases",
        ["disease_code"],
        unique=False,
    )
    op.create_index(
        op.f("ix_workflow_cases_request_id"),
        "workflow_cases",
        ["request_id"],
        unique=True,
    )

    op.create_table(
        "workflow_events",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("workflow_case_id", sa.UUID(), nullable=False),
        sa.Column("sequence", sa.Integer(), nullable=False),
        sa.Column("event_type", sa.String(length=50), nullable=False),
        sa.Column("actor_id", sa.String(length=100), nullable=True),
        sa.Column("details", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(
            ["workflow_case_id"], ["workflow_cases.id"], ondelete="CASCADE"
        ),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        op.f("ix_workflow_events_workflow_case_id"),
        "workflow_events",
        ["workflow_case_id"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(
        op.f("ix_workflow_events_workflow_case_id"),
        table_name="workflow_events",
    )
    op.drop_table("workflow_events")
    op.drop_index(op.f("ix_workflow_cases_request_id"), table_name="workflow_cases")
    op.drop_index(op.f("ix_workflow_cases_disease_code"), table_name="workflow_cases")
    op.drop_index(op.f("ix_workflow_cases_crop_season_id"), table_name="workflow_cases")
    op.drop_table("workflow_cases")
    op.drop_column("treatment_applications", "rule_source")
    op.drop_column("treatment_applications", "rule_version")
    op.drop_column("treatment_applications", "active_ingredient")
    op.drop_column("treatment_applications", "pesticide_name")
