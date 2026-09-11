"""Stage 6/7: CV model metadata and disease-scoped RAG.

Revision ID: d6e7a1202601
Revises: c1f7e2d4a9b0
"""

import sqlalchemy as sa
from alembic import op

revision = "d6e7a1202601"
down_revision = "c1f7e2d4a9b0"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("diagnoses", sa.Column("model_name", sa.String(100), nullable=True))
    op.add_column("documents", sa.Column("disease_code", sa.String(100), nullable=True))
    op.create_index("ix_documents_disease_code", "documents", ["disease_code"])


def downgrade():
    op.drop_index("ix_documents_disease_code", table_name="documents")
    op.drop_column("documents", "disease_code")
    op.drop_column("diagnoses", "model_name")
