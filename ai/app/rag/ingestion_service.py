# SPDX-License-Identifier: AGPL-3.0-only
import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from sqlalchemy import select, text
from sqlalchemy.orm import Session

from app.db.models.document import Document
from app.db.models.document_chunk import DocumentChunk
from app.rag.chunker import chunk_text
from app.rag.sources import load_sources

NAME = re.compile(r"[a-z][a-z0-9_-]{0,49}")


@dataclass(frozen=True)
class Corpus:
    """One namespace and document type of the knowledge base and the files it is built from."""

    namespace: str
    document_type: str
    version: str
    documents_file: Path
    sources_file: Path

    @property
    def lock_key(self) -> int:
        """Serializes replacements of this corpus, including simultaneous CLI runs."""
        digest = hashlib.sha256(f"{self.namespace}:{self.document_type}".encode())
        return int.from_bytes(digest.digest()[:7], "big")


def load_corpus(corpus: Corpus) -> list[dict[str, Any]]:
    """Load the documents of a corpus and validate them against its source registry.

    A document is an object with `code`, `title`, `content` and `source_ids`.
    """

    if not NAME.fullmatch(corpus.namespace):
        raise ValueError("namespace must match [a-z][a-z0-9_-]{0,49}")
    if not corpus.document_type or len(corpus.document_type) > 100:
        raise ValueError("document_type must have 1 to 100 characters")

    with corpus.documents_file.open(encoding="utf-8") as file:
        documents = json.load(file)

    if not isinstance(documents, list):
        raise TypeError(f"{corpus.documents_file.name} must contain a JSON array")

    sources = load_sources(corpus.sources_file)
    seen: set[str] = set()

    for index, item in enumerate(documents, start=1):
        if not isinstance(item, dict):
            raise TypeError(f"Document {index} must be an object")

        missing = {"code", "title", "content", "source_ids"} - item.keys()
        if missing:
            raise ValueError(
                f"Document {index} is missing fields: {', '.join(sorted(missing))}"
            )

        code = item["code"]
        if not isinstance(code, str) or not code or len(code) > 100:
            raise ValueError(f"Document {index} has an invalid code")
        if code.casefold() in seen:
            raise ValueError(f"Duplicate code: {code}")
        seen.add(code.casefold())

        title = item["title"]
        if not isinstance(title, str) or not title or len(title) > 255:
            raise ValueError(f"Document {code} has an invalid title")

        content = item["content"]
        if not isinstance(content, str) or not content.strip():
            raise ValueError(f"Document {code} has empty content")

        source_ids = item["source_ids"]
        if (
            not isinstance(source_ids, list)
            or not source_ids
            or not all(isinstance(source_id, str) for source_id in source_ids)
        ):
            raise ValueError(f"Document {code} has invalid source_ids")
        unknown = [source_id for source_id in source_ids if source_id not in sources]
        if unknown:
            raise ValueError(
                f"Document {code} references unknown sources: {', '.join(unknown)}"
            )

    return documents


def preview_corpus(corpus: Corpus) -> list[dict[str, Any]]:
    return [
        {
            "code": item["code"],
            "title": item["title"],
            "source_ids": item["source_ids"],
            "chunk_count": len(chunk_text(item["content"])),
            "content": item["content"],
        }
        for item in load_corpus(corpus)
    ]


def ingest_corpus(db: Session, corpus: Corpus) -> dict[str, int]:
    """Replace one corpus atomically; retain other namespaces and document types."""
    try:
        return _ingest_corpus(db, corpus)
    except Exception:
        db.rollback()
        raise


def _ingest_corpus(db: Session, corpus: Corpus) -> dict[str, int]:
    from app.rag.embedding_service import EmbeddingService

    documents = load_corpus(corpus)
    prepared = [
        (item, list(dict.fromkeys(chunk_text(item["content"])))) for item in documents
    ]
    # The title leads each embedded text so a chunk matches questions that name its topic.
    embedded_texts = [
        f"{item['title']}\n{content}" for item, chunks in prepared for content in chunks
    ]

    embeddings = iter(
        EmbeddingService().embed_texts(embedded_texts) if embedded_texts else []
    )

    db.execute(text(f"SELECT pg_advisory_xact_lock({corpus.lock_key})"))
    existing_documents = db.scalars(
        select(Document).where(
            Document.document_type == corpus.document_type,
            Document.namespace == corpus.namespace,
        )
    ).all()

    replaced_count = len(existing_documents)
    for document in existing_documents:
        db.delete(document)
    db.flush()

    chunks_inserted = 0
    for item, chunks in prepared:
        document = Document(
            title=item["title"],
            source=",".join(item["source_ids"]),
            document_type=corpus.document_type,
            namespace=corpus.namespace,
            version=corpus.version,
            code=item["code"],
        )
        db.add(document)
        db.flush()

        for chunk_index, content in enumerate(chunks):
            db.add(
                DocumentChunk(
                    document_id=document.id,
                    chunk_index=chunk_index,
                    content=content,
                    page_number=None,
                    embedding=next(embeddings),
                )
            )
            chunks_inserted += 1

    db.commit()

    return {
        "documents_inserted": len(documents),
        "chunks_inserted": chunks_inserted,
        "documents_replaced": replaced_count,
    }
