from sqlalchemy import select
from sqlalchemy.orm import Session, joinedload

from app.db.models.document import Document
from app.db.models.document_chunk import DocumentChunk
from app.rag.embedding_service import EmbeddingService


class RAGRetriever:
    def __init__(self):
        self.embedding_service = EmbeddingService()

    def retrieve(
        self,
        db: Session,
        query: str,
        top_k: int = 5,
        document_type: str = "disease_knowledge",
    ) -> list[dict]:
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
                }
            )

        return results
