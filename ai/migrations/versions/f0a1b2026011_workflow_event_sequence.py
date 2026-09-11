"""Make workflow audit sequence unique per case.

Revision ID: f0a1b2026011
Revises: e8f9a1026011
Create Date: 2026-09-11
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "f0a1b2026011"
down_revision: str | Sequence[str] | None = "e8f9a1026011"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    exists = op.get_bind().scalar(
        sa.text(
            "SELECT EXISTS ("
            "SELECT 1 FROM pg_constraint "
            "WHERE conname = 'uq_workflow_events_case_sequence'"
            ")"
        )
    )
    if not exists:
        op.create_unique_constraint(
            "uq_workflow_events_case_sequence",
            "workflow_events",
            ["workflow_case_id", "sequence"],
        )


def downgrade() -> None:
    exists = op.get_bind().scalar(
        sa.text(
            "SELECT EXISTS ("
            "SELECT 1 FROM pg_constraint "
            "WHERE conname = 'uq_workflow_events_case_sequence'"
            ")"
        )
    )
    if exists:
        op.drop_constraint(
            "uq_workflow_events_case_sequence",
            "workflow_events",
            type_="unique",
        )
