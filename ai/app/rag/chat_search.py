# SPDX-License-Identifier: Apache-2.0
"""Shared chatbot retrieval; the legacy disease retriever is independent."""

import json
import math
import re
import time
import unicodedata
from collections import OrderedDict
from functools import lru_cache
from threading import BoundedSemaphore, Lock

from rank_bm25 import BM25Okapi
from sqlalchemy import select
from sqlalchemy.orm import joinedload

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
        distance = DocumentChunk.embedding.cosine_distance(vector)
        stmt = (
            select(DocumentChunk, distance.label("distance"))
            .join(DocumentChunk.document)
            .options(joinedload(DocumentChunk.document))
            .where(
                Document.namespace == namespace, DocumentChunk.embedding.is_not(None)
            )
            .order_by(DocumentChunk.id)
        )
        if document_type:
            stmt = stmt.where(Document.document_type == document_type)
        policy = profiles()[namespace]
        normalized = fold(question)
        overview = any(
            fold(term) in normalized for term in policy["overview_terms"]
        ) and not any(fold(term) in normalized for term in policy["specific_terms"])
        if overview and not disease_code and not document_type:
            stmt = stmt.where(
                Document.document_type.in_(policy["overview_document_types"])
            )
        if disease_code:
            stmt = stmt.where(Document.disease_code == disease_code)
        # Own the short read transaction; no connection is held during reranking.
        with db.begin():
            rows = db.execute(stmt).all()
            passages = []
            for chunk, dist in rows:
                doc = chunk.document
                source_ids = [s for s in (doc.source or "").split(",") if s]
                if overview and not allowed_for_audience(question, source_ids, policy):
                    continue
                passages.append(
                    dict(
                        chunk_id=str(chunk.id),
                        document_id=str(doc.id),
                        document_title=doc.title,
                        document_version=doc.version,
                        document_type=doc.document_type,
                        disease_code=doc.disease_code,
                        namespace=namespace,
                        chunk_index=chunk.chunk_index,
                        content=chunk.content,
                        page_number=chunk.page_number,
                        source_ids=source_ids,
                        distance=float(dist),
                        score=1 - float(dist),
                        similarity=1 - float(dist),
                        score_kind="cosine",
                    )
                )
        if not passages:
            return result
        identifiers = re.findall(r"(?i)\b[a-z]+[0-9][a-z0-9-]*\b", question)
        if any(
            fold(identifier)
            not in fold(
                " ".join(p["document_title"] + " " + p["content"] for p in passages)
            )
            for identifier in identifiers
        ):
            return {**result, "reason_code": "UNKNOWN_SUBJECT"}
        result["corpus_version"] = ";".join(
            sorted(
                {
                    p["document_type"] + ":" + (p["document_version"] or "")
                    for p in passages
                }
            )
        )
        return self.rank(question, passages, top_k, result, started)

    def rank(self, question, passages, top_k=4, result=None, started=None):
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
        # ponytail: full-corpus BM25 is bounded to small knowledge bases; move to a disk index when needed.
        if len(passages) > 20000:
            raise ValueError("Chat corpus exceeds the in-memory index limit")
        signature = tuple(
            (
                p["chunk_id"],
                p["document_title"],
                p["content"],
                p.get("document_version"),
            )
            for p in passages
        )
        with self._cache_lock:
            bm25 = self._cache.get(signature)
            if bm25 is None:
                bm25 = BM25Okapi(
                    [
                        tokens(p["document_title"] + "\n" + p["content"]) or [""]
                        for p in passages
                    ]
                )
                self._cache[signature] = bm25
                while len(self._cache) > 4:
                    self._cache.popitem(last=False)
            else:
                self._cache.move_to_end(signature)
        scores = bm25.get_scores(tokens(question))
        for p, score in zip(passages, scores):
            p["bm25_score"] = float(score)
        count = self.settings.chat_candidate_count
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
        selected = selected[:top_k]
        result["passages"] = [
            {**p, "content": safe_content, "rank": i}
            for i, p in enumerate(selected, 1)
            if (
                safe_content := (
                    redact_medication_lines(p["content"])
                    if result["namespace"] == "farm"
                    else p["content"]
                )
            )
        ]
        if result["passages"]:
            result.update(scope_status="ANSWERABLE", reason_code="EVIDENCE_FOUND")
        result["timings_ms"]["total"] = (time.perf_counter() - started) * 1000
        return result
