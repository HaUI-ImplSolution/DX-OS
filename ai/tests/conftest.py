import os

# app.db.session builds its engine at import time; the URL is never connected to in unit tests.
os.environ.setdefault("DATABASE_URL", "postgresql+psycopg://test:test@localhost:5432/test")
