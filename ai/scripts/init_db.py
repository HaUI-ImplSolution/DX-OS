# SPDX-License-Identifier: AGPL-3.0-only
"""Create the pgvector extension and the tables of the knowledge base; safe to run again.

    python -m scripts.init_db

Uses RAG_INGEST_DATABASE_URL, whose user may create tables.
"""
from sqlalchemy import create_engine, text

from app.core.config import get_settings
from app.db.base import Base
from app.db.models import document, document_chunk  # noqa: F401  (registers the tables)


def main() -> None:
    database_url = (get_settings().rag_ingest_database_url or "").strip()
    if not database_url:
        raise RuntimeError("RAG_INGEST_DATABASE_URL is not configured")

    engine = create_engine(database_url)
    try:
        with engine.begin() as connection:
            connection.execute(text("CREATE EXTENSION IF NOT EXISTS vector"))
        Base.metadata.create_all(engine)
        print("Knowledge base tables are ready")
    finally:
        engine.dispose()


if __name__ == "__main__":
    main()
