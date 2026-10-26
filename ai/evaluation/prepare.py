"""Bootstrap the isolated vector database and write reproducibility metadata."""

from __future__ import annotations

import importlib.metadata
import json
import os
import platform
import re
from datetime import datetime, timezone
from pathlib import Path

from sqlalchemy import create_engine, text
from sqlalchemy.orm import Session

from app.db.base import Base
from app.db.models import document, document_chunk  # noqa: F401 - register models
from app.rag.ingestion_service import CORPORA, ingest_corpus


def versions() -> dict[str, str]:
    return {
        name: importlib.metadata.version(name)
        for name in ("torch", "sentence-transformers", "transformers", "SQLAlchemy", "pgvector")
    }


def main() -> None:
    admin_url = os.environ["EVAL_ADMIN_DATABASE_URL"]
    read_only_user = os.environ["EVAL_READONLY_USER"]
    read_only_password = os.environ["EVAL_READONLY_PASSWORD"]
    database = os.environ["EVAL_POSTGRES_DB"]
    for value, label in ((read_only_user, "readonly role"), (database, "database")):
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]{0,62}", value):
            raise ValueError(f"Invalid {label} identifier")
    output = Path(os.environ["EVAL_INGEST_RECORD"])
    engine = create_engine(admin_url, pool_pre_ping=True)
    with engine.begin() as connection:
        connection.execute(text("CREATE EXTENSION IF NOT EXISTS vector"))
        connection.execute(text("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\""))
    Base.metadata.create_all(engine)
    with engine.begin() as connection:
        connection.execute(text(f'DROP ROLE IF EXISTS "{read_only_user}"'))
        connection.execute(text(f'CREATE ROLE "{read_only_user}" LOGIN PASSWORD :password'), {"password": read_only_password})
        connection.execute(text(f'GRANT CONNECT ON DATABASE "{database}" TO "{read_only_user}"'))
        connection.execute(text(f'GRANT USAGE ON SCHEMA public TO "{read_only_user}"'))
        connection.execute(text(f'GRANT SELECT ON ALL TABLES IN SCHEMA public TO "{read_only_user}"'))
        connection.execute(text(f'ALTER ROLE "{read_only_user}" SET default_transaction_read_only = on'))
    with Session(engine) as db:
        results = [ingest_corpus(db, corpus) for corpus in CORPORA.values()]
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({
        "embedding_model": os.environ["EMBEDDING_MODEL"],
        "model_artifact": str(Path(os.environ.get("HF_HOME", "/app/weights"))),
        "corpora": {name: corpus.version for name, corpus in CORPORA.items()},
        "runtime_versions": versions(),
        "python": platform.python_version(),
        "completed_at": datetime.now(timezone.utc).isoformat(),
        "documents": results,
        "database_role": read_only_user,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared isolated RAG database; provenance written to {output}")


if __name__ == "__main__":
    main()
