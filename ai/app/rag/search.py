# SPDX-License-Identifier: AGPL-3.0-only
"""Hybrid search over the knowledge base: BM25 and cosine similarity fused by rank."""

import heapq
import re
import time
import unicodedata
from collections import OrderedDict
from threading import Lock

from rank_bm25 import BM25Okapi
from sqlalchemy import select, text

from app.core.config import get_settings
from app.db.models.document import Document
from app.db.models.document_chunk import DocumentChunk
from app.rag.embedding_service import EmbeddingService

MAX_CHUNKS = 20000
RRF_K = 60


def fold(value: str) -> str:
    return "".join(
        ch
        for ch in unicodedata.normalize("NFD", value.casefold().replace("đ", "d"))
        if unicodedata.category(ch) != "Mn"
    )


def tokens(value: str) -> list[str]:
    return re.findall(r"\w+", fold(value))


def _tie_key(passage: dict) -> tuple:
    return (passage["document_title"], passage["chunk_index"], passage["chunk_id"])


class HybridSearch:
    """Returns the passages of one namespace that answer a question, or says why none does.

    Status is FOUND when at least one passage reaches the cosine threshold. A question that names
    a code, such as ABC123, that no document contains has no evidence.
    """

    _cache: OrderedDict = OrderedDict()
    _cache_lock = Lock()

    def __init__(self, embedding_service=None):
        self.embedding_service = embedding_service
        self.settings = get_settings()

    def search(
        self,
        db,
        question,
        namespace="default",
        top_k=4,
        document_type=None,
        code=None,
    ):
        if not question.strip() or not 1 <= top_k <= 20:
            raise ValueError("Nonempty query and top_k between 1 and 20 are required")
        started = time.perf_counter()
        result = dict(
            query=question,
            namespace=namespace,
            status="NO_EVIDENCE",
            reason_code="NO_EVIDENCE",
            corpus_version=None,
            strategy="hybrid",
            passages=[],
            timings_ms={},
        )
        embedder = self.embedding_service or EmbeddingService()
        vector = embedder.embed_text(question)
        result["timings_ms"]["embedding"] = (time.perf_counter() - started) * 1000

        stmt = select(Document).where(Document.namespace == namespace)
        if document_type:
            stmt = stmt.where(Document.document_type == document_type)
        if code:
            stmt = stmt.where(Document.code == code)
        # A replacement must not mix old cached chunks with newly committed vectors.
        with db.begin():
            db.execute(
                text("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY")
            )
            documents = [
                dict(
                    document_id=str(doc.id),
                    document_title=doc.title,
                    document_version=doc.version,
                    document_type=doc.document_type,
                    code=doc.code,
                    namespace=doc.namespace,
                    source_ids=tuple(s for s in (doc.source or "").split(",") if s),
                )
                for doc in db.scalars(stmt.order_by(Document.id)).all()
            ]
            if not documents:
                return result
            index, bm25, searchable_text = self._index(db, documents)
            if not index:
                return result
            identifiers = re.findall(
                r"(?i)\b[a-z]+[0-9][a-z0-9-]*\b",
                unicodedata.normalize("NFKC", question),
            )
            if any(fold(identifier) not in searchable_text for identifier in identifiers):
                return {**result, "reason_code": "UNKNOWN_SUBJECT"}
            result["corpus_version"] = ";".join(
                sorted(
                    {
                        (d["document_type"] or "") + ":" + (d["document_version"] or "")
                        for d in documents
                    }
                )
            )
            distance = DocumentChunk.embedding.cosine_distance(vector)
            eligible = (
                select(DocumentChunk.id, distance.label("distance"))
                .join(DocumentChunk.document)
                .where(
                    DocumentChunk.document_id.in_([d["document_id"] for d in documents]),
                    DocumentChunk.embedding.is_not(None),
                )
            )
            count = self.settings.search_candidate_count
            dense_rows = db.execute(
                eligible.order_by(
                    distance,
                    Document.title,
                    DocumentChunk.chunk_index,
                    DocumentChunk.id,
                ).limit(count)
            ).all()
            scores = bm25.get_scores(tokens(question))
            by_id = {p["chunk_id"]: (p, float(s)) for p, s in zip(index, scores)}
            dense = [
                dict(
                    by_id[str(row.id)][0],
                    bm25_score=by_id[str(row.id)][1],
                    score=1 - float(row.distance),
                    similarity=1 - float(row.distance),
                    distance=float(row.distance),
                )
                for row in dense_rows
            ]
            sparse_indices = heapq.nsmallest(
                count,
                (i for i, score in enumerate(scores) if score > 0),
                key=lambda i: (-scores[i], _tie_key(index[i])),
            )
            sparse_ids = [index[i]["chunk_id"] for i in sparse_indices]
            sparse_distances = (
                {
                    str(row.id): float(row.distance)
                    for row in db.execute(
                        eligible.where(DocumentChunk.id.in_(sparse_ids))
                    ).all()
                }
                if sparse_ids
                else {}
            )
            sparse = [
                dict(
                    index[i],
                    bm25_score=float(scores[i]),
                    score=1 - sparse_distances[index[i]["chunk_id"]],
                    similarity=1 - sparse_distances[index[i]["chunk_id"]],
                    distance=sparse_distances[index[i]["chunk_id"]],
                )
                for i in sparse_indices
                if index[i]["chunk_id"] in sparse_distances
            ]
        # No DB connection is held past this point.
        return self.rank(result, dense, sparse, top_k, started)

    def rank(self, result, dense, sparse, top_k, started):
        """Fuse the two rankings by reciprocal rank and keep the passages that reach the cosine
        threshold."""
        threshold = self.settings.search_cosine_min_score
        if not dense or max(p["score"] for p in dense) < threshold:
            result["reason_code"] = "LOW_RELEVANCE"
            result["timings_ms"]["total"] = (time.perf_counter() - started) * 1000
            return result
        fused = {}
        for ranking in (dense, sparse):
            for rank, passage in enumerate(ranking, 1):
                value = fused.setdefault(passage["chunk_id"], {**passage, "rrf_score": 0.0})
                value["rrf_score"] += 1 / (RRF_K + rank)
        candidates = sorted(
            fused.values(), key=lambda p: (-p["rrf_score"], _tie_key(p))
        )[: self.settings.search_candidate_count]
        selected = [p for p in candidates if p["score"] >= threshold][:top_k]
        result["passages"] = [{**p, "rank": i} for i, p in enumerate(selected, 1)]
        if selected:
            result.update(status="FOUND", reason_code="EVIDENCE_FOUND")
        else:
            result["reason_code"] = "LOW_RELEVANCE"
        result["timings_ms"]["total"] = (time.perf_counter() - started) * 1000
        return result

    def _index(self, db, documents):
        # Ingest replaces documents with new UUIDs, even at the same corpus version.
        # BM25 is built in memory from the chunks, capped at MAX_CHUNKS.
        signature = tuple(tuple(d.items()) for d in documents)
        with self._cache_lock:
            cached = self._cache.get(signature)
            if cached is None:
                metadata = {d["document_id"]: d for d in documents}
                rows = db.execute(
                    select(
                        DocumentChunk.id,
                        DocumentChunk.document_id,
                        DocumentChunk.chunk_index,
                        DocumentChunk.content,
                        DocumentChunk.page_number,
                    )
                    .where(
                        DocumentChunk.document_id.in_(metadata),
                        DocumentChunk.embedding.is_not(None),
                    )
                    .order_by(DocumentChunk.id)
                    .limit(MAX_CHUNKS + 1)
                ).all()
                if len(rows) > MAX_CHUNKS:
                    raise ValueError("Corpus exceeds the in-memory index limit")
                entries = tuple(
                    dict(
                        metadata[str(row.document_id)],
                        chunk_id=str(row.id),
                        chunk_index=row.chunk_index,
                        content=row.content,
                        page_number=row.page_number,
                    )
                    for row in rows
                )
                cached = (
                    entries,
                    (
                        BM25Okapi(
                            [
                                tokens(p["document_title"] + "\n" + p["content"]) or [""]
                                for p in entries
                            ]
                        )
                        if entries
                        else None
                    ),
                    fold(" ".join(p["document_title"] + " " + p["content"] for p in entries)),
                )
                self._cache[signature] = cached
                while len(self._cache) > 4:
                    self._cache.popitem(last=False)
            self._cache.move_to_end(signature)
            return cached
