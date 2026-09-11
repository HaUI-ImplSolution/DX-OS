"""Update treatment rule dose range and add management policies.

Revision ID: c1f7e2d4a9b0
Revises: 4c2a0f9d8b71
Create Date: 2026-09-10
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "c1f7e2d4a9b0"
down_revision: str | Sequence[str] | None = "4c2a0f9d8b71"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "treatment_rules",
        sa.Column("dose_min", sa.Float(), nullable=True),
    )
    op.add_column(
        "treatment_rules",
        sa.Column("dose_max", sa.Float(), nullable=True),
    )
    op.execute(
        "UPDATE treatment_rules "
        "SET dose_min = dose, dose_max = dose "
        "WHERE dose_min IS NULL OR dose_max IS NULL"
    )
    op.alter_column(
        "treatment_rules",
        "dose_min",
        existing_type=sa.Float(),
        nullable=False,
    )
    op.alter_column(
        "treatment_rules",
        "dose_max",
        existing_type=sa.Float(),
        nullable=False,
    )
    op.drop_column("treatment_rules", "dose")
    op.create_check_constraint(
        "ck_treatment_rules_dose_range",
        "treatment_rules",
        "dose_min <= dose_max",
    )
    op.create_check_constraint(
        "ck_treatment_rules_phi_days_positive",
        "treatment_rules",
        "phi_days > 0",
    )

    op.create_table(
        "disease_management_policies",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("disease_id", sa.UUID(), nullable=False),
        sa.Column("management_status", sa.String(length=100), nullable=False),
        sa.Column(
            "direct_treatment_rule_allowed",
            sa.Boolean(),
            nullable=False,
        ),
        sa.Column("chemical_rule_scope", sa.String(length=255), nullable=False),
        sa.Column("source", sa.String(length=255), nullable=True),
        sa.Column("version", sa.String(length=50), nullable=False),
        sa.Column("notes", sa.Text(), nullable=True),
        sa.ForeignKeyConstraint(["disease_id"], ["diseases.id"]),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        op.f("ix_disease_management_policies_disease_id"),
        "disease_management_policies",
        ["disease_id"],
        unique=True,
    )


def downgrade() -> None:
    op.execute(
        """
        DO $$
        BEGIN
            IF EXISTS (
                SELECT 1
                FROM treatment_rules
                WHERE dose_min <> dose_max
            ) THEN
                RAISE EXCEPTION
                    'Cannot downgrade: ranged treatment doses would be lost';
            END IF;
        END
        """
    )

    op.drop_index(
        op.f("ix_disease_management_policies_disease_id"),
        table_name="disease_management_policies",
    )
    op.drop_table("disease_management_policies")

    op.drop_constraint(
        "ck_treatment_rules_phi_days_positive",
        "treatment_rules",
        type_="check",
    )
    op.drop_constraint(
        "ck_treatment_rules_dose_range",
        "treatment_rules",
        type_="check",
    )
    op.add_column(
        "treatment_rules",
        sa.Column("dose", sa.Float(), nullable=True),
    )
    op.execute("UPDATE treatment_rules SET dose = dose_min")
    op.alter_column(
        "treatment_rules",
        "dose",
        existing_type=sa.Float(),
        nullable=False,
    )
    op.drop_column("treatment_rules", "dose_max")
    op.drop_column("treatment_rules", "dose_min")
