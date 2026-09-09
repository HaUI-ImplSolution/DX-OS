import json
from pathlib import Path

from app.db.models.document import Document
from app.db.models.document_chunk import DocumentChunk
from sqlalchemy import select
from sqlalchemy.orm import Session

DATA_FILE = (
    Path(__file__).resolve().parents[2]
    / "data"
    / "structured"
    / "diseases.json"
)

SOURCE = "diseases.json"
DOCUMENT_TYPE = "disease_knowledge"
VERSION = "1.0"


def load_disease_knowledge() -> list[dict[str, str]]:
    with open(DATA_FILE, "r", encoding="utf-8") as f:
        diseases = json.load(f)

    if not isinstance(diseases, list):
        raise TypeError("diseases.json must contain a JSON array")

    required_fields = {"code", "name", "description"}
    codes: set[str] = set()

    for index, item in enumerate(diseases, start=1):
        if not isinstance(item, dict):
            raise TypeError(f"Disease item {index} must be an object")

        missing = required_fields - item.keys()
        if missing:
            missing_text = ", ".join(sorted(missing))
            raise ValueError(
                f"Disease item {index} is missing fields: {missing_text}"
            )

        code = item["code"]
        if not isinstance(code, str) or not code:
            raise ValueError(f"Disease item {index} has an invalid code")
        if code in codes:
            raise ValueError(f"Duplicate disease code: {code}")
        codes.add(code)

        if not isinstance(item["name"], str) or not item["name"]:
            raise ValueError(f"Disease item {index} has an invalid name")
        if not isinstance(item["description"], str):
            raise TypeError(
                f"Disease item {index} has an invalid description"
            )

    return diseases


def build_disease_content(item: dict[str, str]) -> str:
    return (
        f"Mã bệnh: {item['code']}\n"
        f"Tên bệnh: {item['name']}\n"
        f"Mô tả: {item['description']}"
    )


def preview_disease_knowledge() -> list[dict[str, str]]:
    return [
        {
            "code": item["code"],
            "title": item["name"],
            "content": build_disease_content(item),
        }
        for item in load_disease_knowledge()
    ]


def ingest_disease_knowledge(db: Session) -> dict[str, int]:
    from app.rag.embedding_service import EmbeddingService

    diseases = load_disease_knowledge()
    contents = [build_disease_content(item) for item in diseases]

    embedding_service = EmbeddingService()
    embeddings = embedding_service.embed_texts(contents)

    existing_documents = db.scalars(
        select(Document).where(
            Document.source == SOURCE,
            Document.document_type == DOCUMENT_TYPE,
        )
    ).all()

    replaced_count = len(existing_documents)
    for document in existing_documents:
        db.delete(document)
    db.flush()

    for item, content, embedding in zip(
        diseases,
        contents,
        embeddings,
        strict=True,
    ):
        document = Document(
            title=item["name"],
            source=SOURCE,
            document_type=DOCUMENT_TYPE,
            version=VERSION,
        )
        db.add(document)
        db.flush()

        chunk = DocumentChunk(
            document_id=document.id,
            chunk_index=0,
            content=content,
            page_number=None,
            embedding=embedding,
        )
        db.add(chunk)

    db.commit()

    return {
        "documents_inserted": len(diseases),
        "chunks_inserted": len(diseases),
        "documents_replaced": replaced_count,
    }
