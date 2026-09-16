from collections.abc import Generator

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from app.core.config import get_settings

settings = get_settings()
DATABASE_URL = settings.database_url

if not DATABASE_URL:
    raise RuntimeError("DATABASE_URL is not configured")

engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,
)

SessionLocal = sessionmaker(
    bind=engine,
    autoflush=False,
    autocommit=False,
)


def get_db() -> Generator[Session, None, None]:
    """Provide one SQLAlchemy session per FastAPI request."""

    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


# Job ingest RAG cần quyền ghi (INSERT/UPDATE/DELETE) trên documents/document_chunks, khác với
# DATABASE_URL chỉ-đọc mà runtime suy luận dùng (role ai_vector_ro so với ai_vector_ingest).
INGEST_DATABASE_URL = settings.ingest_database_url or DATABASE_URL

ingest_engine = create_engine(
    INGEST_DATABASE_URL,
    pool_pre_ping=True,
)

IngestSessionLocal = sessionmaker(
    bind=ingest_engine,
    autoflush=False,
    autocommit=False,
)
