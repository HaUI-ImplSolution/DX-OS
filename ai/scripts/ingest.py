# SPDX-License-Identifier: AGPL-3.0-only
"""Preview or replace one corpus of the knowledge base.

    python -m scripts.ingest --documents docs.json --sources sources.json [--namespace default]
        [--document-type document] [--version v1] [--dry-run]

docs.json is an array of {code, title, content, source_ids}; sources.json is an array of
{source_id, title, url, license}. Ingest writes with RAG_INGEST_DATABASE_URL.
"""
import argparse
from pathlib import Path

from app.rag.ingestion_service import Corpus, ingest_corpus, preview_corpus


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--documents", type=Path, required=True)
    parser.add_argument("--sources", type=Path, required=True)
    parser.add_argument("--namespace", default="default")
    parser.add_argument("--document-type", default="document")
    parser.add_argument("--version", default="v1")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print the validated documents without writing to PostgreSQL.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=3,
        help="Number of documents shown in dry-run mode (default: 3).",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    corpus = Corpus(
        namespace=args.namespace,
        document_type=args.document_type,
        version=args.version,
        documents_file=args.documents,
        sources_file=args.sources,
    )

    if args.dry_run:
        if args.limit < 0:
            raise ValueError("--limit must be greater than or equal to 0")
        documents = preview_corpus(corpus)
        print(f"Validated documents: {len(documents)}")
        for index, document in enumerate(documents[: args.limit], start=1):
            print(f"\n--- Document {index}: {document['code']} ---")
            print(f"Sources: {', '.join(document['source_ids'])}")
            print(f"Chunks: {document['chunk_count']}")
            print(document["content"])
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
        result = ingest_corpus(db, corpus)
        print(f"Ingested namespace {corpus.namespace}, type {corpus.document_type}")
        print(f"Documents inserted: {result['documents_inserted']}")
        print(f"Chunks inserted: {result['chunks_inserted']}")
        print(f"Previous documents replaced: {result['documents_replaced']}")
    finally:
        db.close()
        engine.dispose()


if __name__ == "__main__":
    main()
