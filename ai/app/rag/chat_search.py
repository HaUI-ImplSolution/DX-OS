# SPDX-License-Identifier: AGPL-3.0-only
"""Shared chatbot retrieval; the legacy disease retriever is independent."""

import json
import heapq
import math
import re
import time
import unicodedata
from collections import OrderedDict
from functools import lru_cache
from threading import BoundedSemaphore, Lock

from rank_bm25 import BM25Okapi
from sqlalchemy import select, text

from app.core.config import get_settings
from app.db.models.document import Document
from app.db.models.document_chunk import DocumentChunk
from app.rag.embedding_service import EmbeddingService
from app.rag.reranker import rerank
from app.rag.safety import redact_medication_lines
from app.rag.source_registry import REGISTRY_FILE


def fold(value: str) -> str:
    return "".join(
        ch
        for ch in unicodedata.normalize("NFD", value.casefold().replace("đ", "d"))
        if unicodedata.category(ch) != "Mn"
    )


def tokens(value: str) -> list[str]:
    return re.findall(r"\w+", fold(value))


@lru_cache(maxsize=1)
def profiles() -> dict:
    return json.loads(
        (REGISTRY_FILE.parent / "chat_profiles.json").read_text(encoding="utf-8")
    )


def outside_scope(question: str, profile: dict) -> bool:
    text = " " + " ".join(tokens(question)) + " "
    contains = lambda term: " " + " ".join(tokens(term)) + " " in text
    foreign = any(contains(t) for t in profile["excluded_terms"]) or any(
        re.search(pattern, text) for pattern in profile.get("excluded_patterns", [])
    )
    comparison = any(
        term in text for term in (" so sanh ", " phan biet ", " so voi ", " khac nhau ")
    )
    return foreign and not (comparison and any(contains(t) for t in profile["terms"]))


def allowed_for_audience(question: str, source_ids: list[str], profile: dict) -> bool:
    text = " " + " ".join(tokens(question)) + " "
    exclusions = profile.get("audience_exclusions", {})
    return not any(
        " " + " ".join(tokens(role)) + " " in text and set(source_ids) & set(blocked)
        for role, blocked in exclusions.items()
    )


def _tie_key(p: dict) -> tuple:
    return (p["document_title"], p["chunk_index"], p.get("document_type") or "")


class ChatSearch:
    _cache: OrderedDict = OrderedDict()
    _cache_lock = Lock()
    _cpu_slot = BoundedSemaphore(1)

    def __init__(self, embedding_service=None, reranker=None, strategy=None):
        self.embedding_service = embedding_service
        self.reranker = reranker or rerank
        self.settings = get_settings()
        self.strategy = strategy or self.settings.chat_retrieval_strategy
        if self.strategy not in {"legacy", "bm25", "hybrid", "hybrid_rerank"}:
            raise ValueError("Unknown retrieval strategy")

    def search(
        self, db, question, namespace, top_k=4, document_type=None, disease_code=None
    ):
        if (
            self.strategy != "hybrid_rerank"
            or namespace in profiles()
            and outside_scope(question, profiles()[namespace])
        ):
            return self._search(
                db, question, namespace, top_k, document_type, disease_code
            )
        started = time.perf_counter()
        # ponytail: serialize CPU search; use a bounded worker pool when throughput needs grow.
        if not self._cpu_slot.acquire(timeout=self.settings.chat_queue_timeout_seconds):
            return dict(
                question=question,
                namespace=namespace,
                scope_status="IN_SCOPE_NO_EVIDENCE",
                reason_code="RERANK_UNAVAILABLE",
                corpus_version=None,
                strategy="hybrid_unavailable",
                passages=[],
                rankings={},
                timings_ms={"queue": (time.perf_counter() - started) * 1000},
            )
        queued = time.perf_counter() - started
        try:
            result = self._search(
                db, question, namespace, top_k, document_type, disease_code
            )
            result["timings_ms"]["queue"] = queued * 1000
            result["timings_ms"]["total"] = (time.perf_counter() - started) * 1000
            return result
        finally:
            self._cpu_slot.release()

    def _search(
        self, db, question, namespace, top_k=4, document_type=None, disease_code=None
    ):
        if not question.strip() or not 1 <= top_k <= 20:
            raise ValueError("Nonempty query and top_k between 1 and 20 are required")
        if namespace not in profiles():
            raise ValueError("Unknown chatbot namespace")
        started = time.perf_counter()
        result = dict(
            question=question,
            namespace=namespace,
            scope_status="IN_SCOPE_NO_EVIDENCE",
            reason_code="NO_EVIDENCE",
            corpus_version=None,
            strategy=self.strategy,
            passages=[],
            rankings={},
            timings_ms={},
        )
        if outside_scope(question, profiles()[namespace]):
            return {
                **result,
                "scope_status": "OUT_OF_SCOPE",
                "reason_code": "EXPLICIT_UNRELATED_TOPIC",
            }
        embedder = self.embedding_service or EmbeddingService()
        vector = embedder.embed_text(question)
        result["timings_ms"]["embedding"] = (time.perf_counter() - started) * 1000
        policy = profiles()[namespace]
        normalized = fold(question)
        overview = any(
            fold(term) in normalized for term in policy["overview_terms"]
        ) and not any(fold(term) in normalized for term in policy["specific_terms"])
        stmt = select(Document).where(Document.namespace == namespace)
        if document_type:
            stmt = stmt.where(Document.document_type == document_type)
        if overview and not disease_code and not document_type:
            stmt = stmt.where(
                Document.document_type.in_(policy["overview_document_types"])
            )
        if disease_code:
            stmt = stmt.where(Document.disease_code == disease_code)
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
                    disease_code=doc.disease_code,
                    namespace=doc.namespace,
                    source_ids=tuple(s for s in (doc.source or "").split(",") if s),
                )
                for doc in db.scalars(stmt.order_by(Document.id)).all()
                if not overview
                or allowed_for_audience(question, (doc.source or "").split(","), policy)
            ]
            if not documents:
                return result
            index, bm25, searchable_text = self._index(db, documents)
            if not index:
                return result
            formulas = {fold(term) for term in policy.get("formula_terms", [])}
            identifiers = re.findall(
                r"(?i)\b[a-z]+[0-9][a-z0-9-]*\b",
                unicodedata.normalize("NFKC", question),
            )
            if any(
                fold(identifier) not in searchable_text
                and fold(identifier) not in formulas
                for identifier in identifiers
            ):
                return {**result, "reason_code": "UNKNOWN_SUBJECT"}
            result["corpus_version"] = ";".join(
                sorted(
                    {
                        d["document_type"] + ":" + (d["document_version"] or "")
                        for d in documents
                    }
                )
            )
            distance = DocumentChunk.embedding.cosine_distance(vector)
            eligible = (
                select(DocumentChunk.id, distance.label("distance"))
                .join(DocumentChunk.document)
                .where(
                    DocumentChunk.document_id.in_(
                        [d["document_id"] for d in documents]
                    ),
                    DocumentChunk.embedding.is_not(None),
                )
            )
            count = self.settings.chat_candidate_count
            dense_rows = db.execute(
                eligible.order_by(
                    distance,
                    Document.title,
                    DocumentChunk.chunk_index,
                    Document.document_type,
                    DocumentChunk.id,
                ).limit(count)
            ).all()
            scores = bm25.get_scores(tokens(question))
            by_id = {
                p["chunk_id"]: (p, float(score)) for p, score in zip(index, scores)
            }
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
        # No DB connection is held during model inference.
        return self.rank(
            question, dense + sparse, top_k, result, started, dense=dense, sparse=sparse
        )

    def _index(self, db, documents):
        # Ingest replaces documents with new UUIDs, even at the same corpus version.
        # ponytail: read metadata per request; BM25 remains O(chunks), capped at 20,000.
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
                    .limit(20001)
                ).all()
                if len(rows) > 20000:
                    raise ValueError("Chat corpus exceeds the in-memory index limit")
                entries = tuple(
                    dict(
                        metadata[str(row.document_id)],
                        chunk_id=str(row.id),
                        chunk_index=row.chunk_index,
                        content=row.content,
                        page_number=row.page_number,
                        score_kind="cosine",
                    )
                    for row in rows
                )
                cached = (
                    entries,
                    (
                        BM25Okapi(
                            [
                                tokens(p["document_title"] + "\n" + p["content"])
                                or [""]
                                for p in entries
                            ]
                        )
                        if entries
                        else None
                    ),
                    fold(
                        " ".join(
                            p["document_title"] + " " + p["content"] for p in entries
                        )
                    ),
                )
                self._cache[signature] = cached
                while len(self._cache) > 4:
                    self._cache.popitem(last=False)
            self._cache.move_to_end(signature)
            return cached

    def rank(
        self,
        question,
        passages,
        top_k=4,
        result=None,
        started=None,
        *,
        dense=None,
        sparse=None,
    ):
        started = started or time.perf_counter()
        result = result or dict(
            question=question,
            namespace=passages[0]["namespace"],
            scope_status="IN_SCOPE_NO_EVIDENCE",
            reason_code="NO_EVIDENCE",
            corpus_version=None,
            strategy=self.strategy,
            passages=[],
            rankings={},
            timings_ms={},
        )
        count = self.settings.chat_candidate_count
        if dense is None or sparse is None:
            passages = [dict(p) for p in passages]
            bm25 = BM25Okapi(
                [
                    tokens(p["document_title"] + "\n" + p["content"]) or [""]
                    for p in passages
                ]
            )
            for p, score in zip(passages, bm25.get_scores(tokens(question))):
                p["bm25_score"] = float(score)
            dense = sorted(passages, key=lambda p: (-p["score"], _tie_key(p)))[:count]
            sparse = sorted(
                (p for p in passages if p["bm25_score"] > 0),
                key=lambda p: (-p["bm25_score"], _tie_key(p)),
            )[:count]
        fused = {}
        for ranking in (dense, sparse):
            for rank, p in enumerate(ranking, 1):
                value = fused.setdefault(p["chunk_id"], {**p, "rrf_score": 0.0})
                value["rrf_score"] += 1 / (60 + rank)
        candidates = sorted(
            fused.values(), key=lambda p: (-p["rrf_score"], _tie_key(p))
        )[:count]
        result["rankings"] = {"cosine": dense, "bm25": sparse, "rrf": candidates}
        result["timings_ms"]["candidates"] = (
            time.perf_counter() - started
        ) * 1000 - result["timings_ms"].get("embedding", 0)
        if self.strategy == "legacy":
            from app.rag.retriever import TOPIC_MATCH_BOOST, _topic_phrases

            named = lambda p: any(
                t in question.lower() for t in _topic_phrases(p["document_title"])
            )
            selected = sorted(
                dense, key=lambda p: -(p["score"] + TOPIC_MATCH_BOOST * named(p))
            )[:top_k]
            selected = [
                p for p in selected if p["score"] >= self.settings.chat_cosine_min_score
            ]
        elif self.strategy == "bm25":
            selected = [
                p
                for p in sparse[:top_k]
                if p["score"] >= self.settings.chat_cosine_min_score
            ]
        elif self.strategy == "hybrid":
            selected = [
                p
                for p in candidates
                if p["score"] >= self.settings.chat_cosine_min_score
            ]
        else:
            if (
                not dense
                or max(p["score"] for p in dense) < self.settings.chat_cosine_min_score
            ):
                result["reason_code"] = "LOW_RELEVANCE"
                return result
            before = time.perf_counter()
            try:
                values = self.reranker(question, candidates)
                if len(values) != len(candidates) or any(
                    not math.isfinite(v) or not 0 <= v <= 1 for v in values
                ):
                    raise ValueError("Invalid reranker scores")
            except (OSError, ValueError, RuntimeError, TimeoutError):
                result["reason_code"] = "RERANK_UNAVAILABLE"
                result["strategy"] = "hybrid_unavailable"
                return result
            for p, value in zip(candidates, values):
                p["rerank_score"] = value
            candidates = sorted(
                candidates,
                key=lambda p: (-p["rerank_score"], -p["rrf_score"], _tie_key(p)),
            )
            result["rankings"]["rerank"] = candidates
            result["timings_ms"]["rerank"] = (time.perf_counter() - before) * 1000
            selected = [
                p
                for p in candidates
                if p["rerank_score"] >= self.settings.chat_rerank_min_score
            ]
        policy = profiles().get(result["namespace"], {})
        if any(
            fold(term) in fold(question) for term in policy.get("overview_terms", [])
        ):
            diverse, counts = [], {}
            for passage in selected:
                key = passage["document_id"]
                if counts.get(key, 0) < 2:
                    diverse.append(passage)
                    counts[key] = counts.get(key, 0) + 1
            selected = diverse
        selected = [
            {**p, "content": safe_content}
            for p in selected
            if (
                safe_content := (
                    redact_medication_lines(p["content"])
                    if result["namespace"] == "farm"
                    else p["content"]
                )
            )
        ]
        result["passages"] = [
            {**p, "rank": i} for i, p in enumerate(selected[:top_k], 1)
        ]
        if result["passages"]:
            result.update(scope_status="ANSWERABLE", reason_code="EVIDENCE_FOUND")
        result["timings_ms"]["total"] = (time.perf_counter() - started) * 1000
        return result
