# SPDX-License-Identifier: Apache-2.0
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from sqlalchemy import select, text
from sqlalchemy.orm import Session

from app.db.models.document import Document
from app.db.models.document_chunk import DocumentChunk
from app.rag.chunker import chunk_text
from app.rag.source_registry import load_source_registry

DATA_ROOT = Path(__file__).resolve().parents[3] / "data" / "rag"


@dataclass(frozen=True)
class Corpus:
    """One namespace of the knowledge base and the files it is built from."""

    namespace: str
    document_type: str
    data_file: Path
    registry_file: Path | None
    version: str
    # Field holding the stable code of a document and the field holding its title.
    code_field: str
    title_field: str
    required_fields: frozenset[str]
    # Whether the code is stored in documents.disease_code (only disease corpora have one).
    code_is_disease_code: bool
    # Serializes replacements of this corpus, including simultaneous CLI runs.
    lock_key: int
    label: str


FARM_CORPUS = Corpus(
    namespace="farm",
    document_type="disease_knowledge",
    data_file=DATA_ROOT / "processed" / "disease_knowledge.json",
    registry_file=None,
    version="riceleafbd-v3-workbook-v1",
    code_field="disease_code",
    title_field="class_name_vi",
    required_fields=frozenset(
        {
            "disease_code",
            "class_name_vi",
            "knowledge",
            "provenance",
            "governance",
            "content",
        }
    ),
    code_is_disease_code=True,
    lock_key=674507,
    label="Disease",
)

LAB_CORPUS = Corpus(
    namespace="lab",
    document_type="lab_knowledge",
    data_file=DATA_ROOT / "lab" / "processed" / "lab_knowledge.json",
    registry_file=DATA_ROOT / "lab" / "source_registry.json",
    version="lab-knowledge-v3",
    code_field="article_code",
    title_field="title",
    required_fields=frozenset({"article_code", "title", "provenance", "content"}),
    code_is_disease_code=False,
    lock_key=674508,
    label="Article",
)

FARM_KNOWLEDGE_CORPUS = Corpus(
    namespace="farm",
    document_type="farm_knowledge",
    data_file=DATA_ROOT / "processed" / "farm_knowledge.json",
    registry_file=DATA_ROOT / "farm_knowledge_source_registry.json",
    version="farm-knowledge-v1",
    code_field="document_id",
    title_field="title",
    required_fields=frozenset(
        {
            "document_id",
            "title",
            "content",
            "provenance",
            "review_status",
            "topic",
            "language",
        }
    ),
    code_is_disease_code=False,
    lock_key=674509,
    label="Farm knowledge",
)

CORPORA = {
    "farm": FARM_CORPUS,
    "farm_knowledge": FARM_KNOWLEDGE_CORPUS,
    "lab": LAB_CORPUS,
}


def selected_corpora(name: str | None = None) -> list[Corpus]:
    """Select the approved corpora for an app without promoting staged content."""
    if name == "farm_knowledge":
        load_corpus(FARM_KNOWLEDGE_CORPUS)
        return [FARM_KNOWLEDGE_CORPUS]
    corpora = (
        [FARM_CORPUS]
        if name == "farm"
        else [LAB_CORPUS]
        if name == "lab"
        else [FARM_CORPUS, LAB_CORPUS]
    )
    if name not in {None, "farm", "lab"}:
        raise ValueError("Unknown corpus selection")
    if name != "lab" and FARM_KNOWLEDGE_CORPUS.data_file.exists():
        load_corpus(FARM_KNOWLEDGE_CORPUS)
        corpora.append(FARM_KNOWLEDGE_CORPUS)
    return corpora


# Kept for callers that predate the corpus argument.
DATA_FILE = FARM_CORPUS.data_file
DOCUMENT_TYPE = FARM_CORPUS.document_type
NAMESPACE = FARM_CORPUS.namespace
VERSION = FARM_CORPUS.version
REQUIRED_FIELDS = set(FARM_CORPUS.required_fields)


def load_corpus(corpus: Corpus) -> list[dict[str, Any]]:
    """Load the processed documents of a corpus and validate their source references."""

    with corpus.data_file.open(encoding="utf-8") as file:
        documents = json.load(file)

    if not isinstance(documents, list):
        raise TypeError(f"{corpus.data_file.name} must contain a JSON array")

    source_registry = load_source_registry(corpus.registry_file)
    if corpus.namespace == "lab" and corpus.registry_file:
        for source_id, source in source_registry.items():
            allowed = json.loads((DATA_ROOT / "chat_profiles.json").read_text())["lab"][
                "allowed_hosts"
            ]
            if urlparse(source["url"]).hostname not in allowed:
                raise ValueError(
                    f"Lab source {source_id} is outside the allowlist: {source['url']}"
                )
    normalized_codes: set[str] = set()

    for index, item in enumerate(documents, start=1):
        if not isinstance(item, dict):
            raise TypeError(f"{corpus.label} item {index} must be an object")

        missing = corpus.required_fields - item.keys()
        if missing:
            missing_text = ", ".join(sorted(missing))
            raise ValueError(
                f"{corpus.label} item {index} is missing fields: {missing_text}"
            )

        code = item[corpus.code_field]
        if not isinstance(code, str) or not code:
            raise ValueError(
                f"{corpus.label} item {index} has an invalid {corpus.code_field}"
            )

        normalized_code = code.casefold()
        if normalized_code in normalized_codes:
            raise ValueError(f"Duplicate {corpus.code_field}: {code}")
        normalized_codes.add(normalized_code)

        title = item[corpus.title_field]
        if not isinstance(title, str) or not title:
            raise ValueError(
                f"{corpus.label} item {index} has an invalid {corpus.title_field}"
            )

        content = item["content"]
        if not isinstance(content, str) or not content.strip():
            raise ValueError(f"{corpus.label} item {index} has empty content")

        provenance = item["provenance"]
        if not isinstance(provenance, dict):
            raise TypeError(f"{corpus.label} item {index} has invalid provenance")

        source_ids = provenance.get("source_ids")
        if (
            not isinstance(source_ids, list)
            or not source_ids
            or not all(isinstance(source_id, str) for source_id in source_ids)
        ):
            raise ValueError(f"{corpus.label} item {index} has invalid source_ids")

        unknown_sources = [
            source_id for source_id in source_ids if source_id not in source_registry
        ]
        if unknown_sources:
            unknown_text = ", ".join(unknown_sources)
            raise ValueError(
                f"{corpus.label} {code} references unknown sources: {unknown_text}"
            )

        if (
            corpus.document_type == "farm_knowledge"
            and item.get("review_status") != "APPROVED"
        ):
            raise ValueError(
                f"Farm knowledge {code} is not approved for ingest: "
                f"{item.get('review_status')!r}"
            )
        if corpus.document_type == "farm_knowledge" and item.get("language") not in {
            "vi",
            "vi-VN",
        }:
            raise ValueError(f"Farm knowledge {code} is not Vietnamese")

    return documents


def preview_corpus(corpus: Corpus) -> list[dict[str, Any]]:
    return [
        {
            "code": item[corpus.code_field],
            "title": item[corpus.title_field],
            "source_ids": item["provenance"]["source_ids"],
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
        f"{item[corpus.title_field]}\n{content}"
        for item, chunks in prepared
        for content in chunks
    ]

    embeddings = iter(EmbeddingService().embed_texts(embedded_texts) if embedded_texts else [])

    db.execute(text(f"SELECT pg_advisory_xact_lock({int(corpus.lock_key)})"))
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
        source_ids = item["provenance"]["source_ids"]
        document = Document(
            title=item[corpus.title_field],
            source=",".join(source_ids),
            document_type=corpus.document_type,
            namespace=corpus.namespace,
            version=corpus.version,
            disease_code=item[corpus.code_field]
            if corpus.code_is_disease_code
            else None,
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


def load_disease_knowledge() -> list[dict[str, Any]]:
    """Load processed disease knowledge and validate its source references."""

    return load_corpus(FARM_CORPUS)


def preview_disease_knowledge() -> list[dict[str, Any]]:
    return preview_corpus(FARM_CORPUS)


def ingest_disease_knowledge(db: Session) -> dict[str, int]:
    """Replace the disease corpus atomically; retain unrelated document types."""
    return ingest_corpus(db, FARM_CORPUS)
