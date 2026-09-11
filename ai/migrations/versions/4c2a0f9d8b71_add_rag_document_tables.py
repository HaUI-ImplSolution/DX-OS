"""add RAG document tables

Revision ID: 4c2a0f9d8b71
Revises: 9700136d7e00
Create Date: 2026-09-09

"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from pgvector.sqlalchemy import Vector

revision: str = "4c2a0f9d8b71"
down_revision: str | Sequence[str] | None = "9700136d7e00"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")

    op.create_table(
        "documents",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("title", sa.String(length=255), nullable=False),
        sa.Column("source", sa.String(length=500), nullable=True),
        sa.Column("document_type", sa.String(length=100), nullable=True),
        sa.Column("version", sa.String(length=50), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )

    op.create_table(
        "document_chunks",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("document_id", sa.UUID(), nullable=False),
        sa.Column("chunk_index", sa.Integer(), nullable=False),
        sa.Column("content", sa.Text(), nullable=False),
        sa.Column("page_number", sa.Integer(), nullable=True),
        sa.Column("embedding", Vector(384), nullable=True),
        sa.ForeignKeyConstraint(
            ["document_id"],
            ["documents.id"],
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        op.f("ix_document_chunks_document_id"),
        "document_chunks",
        ["document_id"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(
        op.f("ix_document_chunks_document_id"),
        table_name="document_chunks",
    )
    op.drop_table("document_chunks")
    op.drop_table("documents")
