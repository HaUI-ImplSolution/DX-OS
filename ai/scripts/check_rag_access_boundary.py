# SPDX-License-Identifier: Apache-2.0
"""Verify that RAG writes stay outside the FastAPI runtime."""

from pathlib import Path

AI_ROOT = Path(__file__).resolve().parents[1]
INGESTION_SERVICE = AI_ROOT / "app" / "rag" / "ingestion_service.py"
WRITE_MARKERS = (
    "db.add(",
    "db.delete(",
    "db.flush(",
    "db.commit(",
    "session.add(",
    "session.delete(",
    "session.flush(",
    "session.commit(",
)


def main() -> None:
    violations: list[str] = []
    for path in (AI_ROOT / "app").rglob("*.py"):
        if path == INGESTION_SERVICE:
            continue
        source = path.read_text(encoding="utf-8")
        if "app.rag.ingestion_service" in source:
            violations.append(f"runtime imports ingestion: {path}")
        for marker in WRITE_MARKERS:
            if marker in source:
                violations.append(f"runtime contains {marker}: {path}")

    ingest_script = (AI_ROOT / "scripts" / "ingest_diseases.py").read_text(
        encoding="utf-8"
    )
    assert "app.db.session" not in ingest_script
    assert "rag_ingest_database_url" in ingest_script
    assert not list((AI_ROOT / "scripts").glob("seed_*.py"))
    assert not violations, "\n".join(violations)
    print("RAG access boundary check: PASS")


if __name__ == "__main__":
    main()
