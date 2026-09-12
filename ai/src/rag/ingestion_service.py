import json
from pathlib import Path
from typing import Any

from sqlalchemy import select, text
from sqlalchemy.orm import Session

from src.db.models.document import Document
from src.db.models.document_chunk import DocumentChunk
from src.rag.chunker import chunk_text
from src.rag.source_registry import load_source_registry

DATA_FILE = (
    Path(__file__).resolve().parents[2]
    / "data"
    / "rag"
    / "processed"
    / "disease_knowledge.json"
)

DOCUMENT_TYPE = "disease_knowledge"
VERSION = "stage7-rich-v1"
REQUIRED_FIELDS = {
    "disease_code",
    "class_name_vi",
    "knowledge",
    "provenance",
    "governance",
    "content",
}


def load_disease_knowledge() -> list[dict[str, Any]]:
    """Load processed disease knowledge and validate its source references."""

    with DATA_FILE.open(encoding="utf-8") as file:
        diseases = json.load(file)

    if not isinstance(diseases, list):
        raise TypeError("disease_knowledge.json must contain a JSON array")

    source_registry = load_source_registry()
    normalized_codes: set[str] = set()

    for index, item in enumerate(diseases, start=1):
        if not isinstance(item, dict):
            raise TypeError(f"Disease item {index} must be an object")

        missing = REQUIRED_FIELDS - item.keys()
        if missing:
            missing_text = ", ".join(sorted(missing))
            raise ValueError(f"Disease item {index} is missing fields: {missing_text}")

        code = item["disease_code"]
        if not isinstance(code, str) or not code:
            raise ValueError(f"Disease item {index} has an invalid disease_code")

        normalized_code = code.casefold()
        if normalized_code in normalized_codes:
            raise ValueError(f"Duplicate disease_code: {code}")
        normalized_codes.add(normalized_code)

        title = item["class_name_vi"]
        if not isinstance(title, str) or not title:
            raise ValueError(f"Disease item {index} has an invalid class_name_vi")

        content = item["content"]
        if not isinstance(content, str) or not content.strip():
            raise ValueError(f"Disease item {index} has empty content")

        provenance = item["provenance"]
        if not isinstance(provenance, dict):
            raise TypeError(f"Disease item {index} has invalid provenance")

        source_ids = provenance.get("source_ids")
        if (
            not isinstance(source_ids, list)
            or not source_ids
            or not all(isinstance(source_id, str) for source_id in source_ids)
        ):
            raise ValueError(f"Disease item {index} has invalid source_ids")

        unknown_sources = [
            source_id for source_id in source_ids if source_id not in source_registry
        ]
        if unknown_sources:
            unknown_text = ", ".join(unknown_sources)
            raise ValueError(
                f"Disease {code} references unknown sources: {unknown_text}"
            )

    return diseases


def preview_disease_knowledge() -> list[dict[str, Any]]:
    return [
        {
            "code": item["disease_code"],
            "title": item["class_name_vi"],
            "source_ids": item["provenance"]["source_ids"],
            "chunk_count": len(chunk_text(item["content"])),
            "content": item["content"],
        }
        for item in load_disease_knowledge()
    ]


def ingest_disease_knowledge(db: Session) -> dict[str, int]:
    """Replace the disease corpus atomically; retain unrelated document types."""
    try:
        return _ingest_disease_knowledge(db)
    except Exception:
        db.rollback()
        raise


def _ingest_disease_knowledge(db: Session) -> dict[str, int]:
    from src.rag.embedding_service import EmbeddingService

    diseases = load_disease_knowledge()
    prepared = [
        (item, list(dict.fromkeys(chunk_text(item["content"])))) for item in diseases
    ]
    chunk_contents = [content for _, chunks in prepared for content in chunks]

    embedding_service = EmbeddingService()
    embeddings = iter(embedding_service.embed_texts(chunk_contents))

    # Serialize replacements of this corpus, including simultaneous CLI runs.
    db.execute(text("SELECT pg_advisory_xact_lock(674507)"))
    existing_documents = db.scalars(
        select(Document).where(Document.document_type == DOCUMENT_TYPE)
    ).all()

    replaced_count = len(existing_documents)
    for document in existing_documents:
        db.delete(document)
    db.flush()

    chunks_inserted = 0
    for item, chunks in prepared:
        source_ids = item["provenance"]["source_ids"]
        document = Document(
            title=item["class_name_vi"],
            source=",".join(source_ids),
            document_type=DOCUMENT_TYPE,
            version=VERSION,
            disease_code=item["disease_code"],
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
        "documents_inserted": len(diseases),
        "chunks_inserted": chunks_inserted,
        "documents_replaced": replaced_count,
    }
