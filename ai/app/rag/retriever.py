from app.db.models.document_chunk import DocumentChunk
from app.rag.embedding_service import EmbeddingService
from sqlalchemy import select
from sqlalchemy.orm import Session


class RAGRetriever:
    def __init__(self):
        self.embedding_service = EmbeddingService()

    def retrieve(
        self,
        db: Session,
        query: str,
        top_k: int = 5,
    ) -> list[dict]:
        query_embedding = self.embedding_service.embed_text(query)

        distance = DocumentChunk.embedding.cosine_distance(
            query_embedding
        )

        stmt = (
            select(
                DocumentChunk,
                distance.label("distance"),
            )
            .where(DocumentChunk.embedding.is_not(None))
            .order_by(distance)
            .limit(top_k)
        )

        rows = db.execute(stmt).all()

        results = []

        for chunk, dist in rows:
            results.append({
                "chunk_id": str(chunk.id),
                "document_id": str(chunk.document_id),
                "content": chunk.content,
                "page_number": chunk.page_number,
                "distance": float(dist),
                "similarity": 1 - float(dist),
            })

        return results