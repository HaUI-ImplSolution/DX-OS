# SPDX-License-Identifier: AGPL-3.0-only
import argparse

from app.rag.ingestion_service import (
    CORPORA,
    ingest_corpus,
    preview_corpus,
    selected_corpora,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Preview or ingest the knowledge of one corpus into pgvector."
    )
    parser.add_argument(
        "--corpus",
        choices=sorted(CORPORA),
        default="farm",
        help="Corpus (namespace) to ingest (default: farm).",
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


def print_preview(corpus: str, limit: int) -> None:
    if limit < 0:
        raise ValueError("--limit must be greater than or equal to 0")

    documents = [
        item
        for selected in selected_corpora(corpus)
        for item in preview_corpus(selected)
    ]
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
        print_preview(args.corpus, args.limit)
        return

    from sqlalchemy import create_engine
    from sqlalchemy.orm import Session

    from app.core.config import get_settings

    database_url = (get_settings().rag_ingest_database_url or "").strip()
    if not database_url:
        raise RuntimeError("RAG_INGEST_DATABASE_URL is not configured")

    engine = create_engine(database_url, pool_pre_ping=True)
    db = Session(engine)

    try:
        from app.rag.reranker import prepare_reranker

        prepare_reranker()
        results = [
            ingest_corpus(db, selected) for selected in selected_corpora(args.corpus)
        ]
        result = {key: sum(item[key] for item in results) for key in results[0]}
        print(f"RAG ingestion completed for corpus {args.corpus}")
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
