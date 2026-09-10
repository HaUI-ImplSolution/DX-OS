from sqlalchemy import select
from sqlalchemy.orm import Session, joinedload

from app.db.models.document import Document
from app.db.models.document_chunk import DocumentChunk
from app.rag.embedding_service import EmbeddingService


class RAGRetriever:
    def __init__(self, embedding_service=None):
        self.embedding_service = embedding_service

    def retrieve(
        self,
        db: Session,
        query: str,
        top_k: int = 5,
        document_type: str = "disease_knowledge",
        disease_code: str | None = None,
    ) -> list[dict]:
        if not query.strip() or not 1 <= top_k <= 20:
            raise ValueError("query must not be empty; top_k must be between 1 and 20")
        if self.embedding_service is None:
            self.embedding_service = EmbeddingService()
        query_embedding = self.embedding_service.embed_text(query)
        distance = DocumentChunk.embedding.cosine_distance(query_embedding)

        stmt = (
            select(
                DocumentChunk,
                distance.label("distance"),
            )
            .join(DocumentChunk.document)
            .options(joinedload(DocumentChunk.document))
            .where(
                DocumentChunk.embedding.is_not(None),
                Document.document_type == document_type,
            )
            .order_by(distance)
            .limit(top_k)
        )
        if disease_code is not None:
            stmt = stmt.where(Document.disease_code == disease_code)

        rows = db.execute(stmt).all()
        results = []

        for chunk, dist in rows:
            document = chunk.document
            source_ids = [
                source_id
                for source_id in (document.source or "").split(",")
                if source_id
            ]
            results.append(
                {
                    "chunk_id": str(chunk.id),
                    "document_id": str(chunk.document_id),
                    "document_title": document.title,
                    "document_version": document.version,
                    "source_ids": source_ids,
                    "content": chunk.content,
                    "page_number": chunk.page_number,
                    "distance": float(dist),
                    "similarity": 1 - float(dist),
                    "score": 1 - float(dist),
                    "disease_code": document.disease_code,
                }
            )

        return results


def retrieve_knowledge(
    db: Session, query: str, disease_code: str | None = None, top_k: int = 3
) -> list[dict]:
    return RAGRetriever().retrieve(db, query, top_k=top_k, disease_code=disease_code)
