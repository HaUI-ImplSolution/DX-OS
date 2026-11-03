# SPDX-License-Identifier: AGPL-3.0-only
import time

from app.rag.search import HybridSearch


def passage(chunk_id, score, title="Tài liệu", index=0):
    return dict(
        chunk_id=chunk_id,
        document_id="doc",
        document_title=title,
        chunk_index=index,
        content="nội dung",
        score=score,
        bm25_score=0.0,
    )


def run(dense, sparse, top_k=4):
    result = dict(status="NO_EVIDENCE", reason_code="NO_EVIDENCE", passages=[], timings_ms={})
    return HybridSearch().rank(result, dense, sparse, top_k, time.perf_counter())


def test_passages_below_the_cosine_threshold_are_not_evidence():
    result = run([passage("a", 0.30)], [passage("a", 0.30)])

    assert result["status"] == "NO_EVIDENCE"
    assert result["reason_code"] == "LOW_RELEVANCE"
    assert result["passages"] == []


def test_a_passage_found_by_both_rankings_comes_first():
    dense = [passage("a", 0.9, index=0), passage("b", 0.8, index=1)]
    sparse = [passage("b", 0.8, index=1), passage("c", 0.7, index=2)]

    result = run(dense, sparse)

    assert result["status"] == "FOUND"
    assert [p["chunk_id"] for p in result["passages"]] == ["b", "a", "c"]
    assert [p["rank"] for p in result["passages"]] == [1, 2, 3]


def test_only_passages_reaching_the_threshold_are_returned_up_to_top_k():
    dense = [passage("a", 0.9), passage("b", 0.8, index=1), passage("c", 0.7, index=2)]
    sparse = [passage("d", 0.2, index=3)]

    result = run(dense, sparse, top_k=2)

    assert len(result["passages"]) == 2
    assert all(p["score"] >= 0.40 for p in result["passages"])


def test_empty_rankings_have_no_relevant_passage():
    assert run([], [])["reason_code"] == "LOW_RELEVANCE"
