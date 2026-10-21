# SPDX-License-Identifier: Apache-2.0
from sqlalchemy import select
from sqlalchemy.orm import Session, joinedload

from app.db.models.document import Document
from app.db.models.document_chunk import DocumentChunk
from app.rag.embedding_service import EmbeddingService


DEFAULT_NAMESPACE = "farm"
# Added to the ranking, not to the score, of passages whose topic the question names.
TOPIC_MATCH_BOOST = 0.15
CANDIDATE_FACTOR = 3
MIN_CANDIDATES = 15


def _topic_phrases(title: str) -> list[str]:
    """Names a document answers to: its title without the "Bệnh" prefix and "trên lúa" suffix."""
    outer, _, rest = title.lower().partition("(")
    name = outer.strip().removeprefix("bệnh ").split(" trên ")[0].strip()
    phrases = [name.removesuffix(" lúa").strip()]
    if rest:
        phrases.append(rest.split(")")[0].strip())
    return [phrase for phrase in phrases if len(phrase) >= 4]


class RAGRetriever:
    def __init__(self, embedding_service=None):
        self.embedding_service = embedding_service

    def retrieve(
        self,
        db: Session,
        query: str,
        top_k: int = 5,
        document_type: str | None = "disease_knowledge",
        disease_code: str | None = None,
        namespace: str = DEFAULT_NAMESPACE,
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
                Document.namespace == namespace,
            )
            .order_by(distance)
            .limit(max(top_k * CANDIDATE_FACTOR, MIN_CANDIDATES))
        )
        if document_type is not None:
            stmt = stmt.where(Document.document_type == document_type)
        if disease_code is not None:
            stmt = stmt.where(Document.disease_code == disease_code)

        rows = db.execute(stmt).all()
        results = []
        question = query.lower()

        for chunk, dist in rows:
            document = chunk.document
            named = any(
                phrase in question for phrase in _topic_phrases(document.title or "")
            )
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
                    "chunk_index": chunk.chunk_index,
                    "source_ids": source_ids,
                    "content": chunk.content,
                    "page_number": chunk.page_number,
                    "distance": float(dist),
                    "similarity": 1 - float(dist),
                    "score": 1 - float(dist),
                    "disease_code": document.disease_code,
                    "_rank": 1 - float(dist) + (TOPIC_MATCH_BOOST if named else 0.0),
                }
            )

        results.sort(key=lambda item: item["_rank"], reverse=True)
        for item in results:
            del item["_rank"]
        return results[:top_k]


def retrieve_knowledge(
    db: Session,
    query: str,
    disease_code: str | None = None,
    top_k: int = 3,
    namespace: str = DEFAULT_NAMESPACE,
) -> list[dict]:
    return RAGRetriever().retrieve(
        db, query, top_k=top_k, disease_code=disease_code, namespace=namespace
    )
