from collections.abc import Generator

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from src.core.config import get_settings
from src.db.base import ReflectedBase

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


def prepare_reflected_tables(target_engine=None) -> None:
    """Reflect master backend tables dynamically from PostgreSQL database."""
    try:
        ReflectedBase.prepare(target_engine or engine)
    except Exception:
        pass


def get_db() -> Generator[Session, None, None]:
    """Provide one SQLAlchemy session per FastAPI request."""
    prepare_reflected_tables()
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
