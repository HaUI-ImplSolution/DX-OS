import argparse

from app.rag.ingestion_service import (
    ingest_disease_knowledge,
    preview_disease_knowledge,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Preview or ingest disease knowledge into pgvector."
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print generated documents without writing to PostgreSQL.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=3,
        help="Number of documents shown in dry-run mode (default: 3).",
    )
    return parser.parse_args()


def print_preview(limit: int) -> None:
    if limit < 0:
        raise ValueError("--limit must be greater than or equal to 0")

    documents = preview_disease_knowledge()
    print(f"Validated documents: {len(documents)}")
    print(f"Previewing documents: {min(limit, len(documents))}")

    for index, document in enumerate(documents[:limit], start=1):
        print(f"\n--- Document {index}: {document['code']} ---")
        print(f"Sources: {', '.join(document['source_ids'])}")
        print(f"Chunks: {document['chunk_count']}")
        print(document["content"])


def main() -> None:
    args = parse_args()

    if args.dry_run:
        print_preview(args.limit)
        return

    from app.core.config import get_settings
    from sqlalchemy import create_engine
    from sqlalchemy.orm import Session

    database_url = (get_settings().rag_ingest_database_url or "").strip()
    if not database_url:
        raise RuntimeError("RAG_INGEST_DATABASE_URL is not configured")

    engine = create_engine(database_url, pool_pre_ping=True)
    db = Session(engine)

    try:
        result = ingest_disease_knowledge(db)
        print("RAG disease ingestion completed")
        print(f"Documents inserted: {result['documents_inserted']}")
        print(f"Chunks inserted: {result['chunks_inserted']}")
        print(f"Previous documents replaced: {result['documents_replaced']}")
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()
        engine.dispose()


if __name__ == "__main__":
    main()
