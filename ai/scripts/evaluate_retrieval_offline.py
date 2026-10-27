# SPDX-License-Identifier: Apache-2.0
"""Offline lexical retrieval smoke evaluation.

This is intentionally labelled a proxy: it exercises qrels, chunk identity,
Recall@k and nDCG@k without pretending to reproduce pgvector embeddings.
The real retrieval report still requires the isolated vector DB runner.
"""

from __future__ import annotations

import argparse
import json
import math
import re
from pathlib import Path

from app.rag.chunker import chunk_text


ROOT = Path(__file__).resolve().parents[2]
TOKEN = re.compile(r"[\wÀ-ỹ]+", re.UNICODE)


def tokens(text: str) -> set[str]:
    return {token.casefold() for token in TOKEN.findall(text)}


def corpus(path: Path, code_field: str) -> list[tuple[str, str]]:
    rows: list[tuple[str, str]] = []
    for item in json.loads(path.read_text(encoding="utf-8")):
        key = item[code_field]
        # Ingestion stores chunks from content; the title is only prepended to
        # the embedding text and must not change chunk identity.
        for index, content in enumerate(chunk_text(item["content"])):
            rows.append((f"farm/{key}/{index}", content))
    return rows


def qrels(path: Path) -> dict[str, dict[str, int]]:
    result: dict[str, dict[str, int]] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        qid, _iteration, document, relevance = line.split()
        result.setdefault(qid, {})[document] = int(relevance)
    return result


def metrics(query: str, ranked: list[str], judged: dict[str, int], k: int) -> tuple[float, float]:
    top = ranked[:k]
    relevant = {doc for doc, gain in judged.items() if gain > 0}
    recall = len(set(top) & relevant) / len(relevant) if relevant else 0.0
    dcg = sum((2 ** judged.get(doc, 0) - 1) / math.log2(index + 2) for index, doc in enumerate(top))
    ideal = sorted(judged.values(), reverse=True)[:k]
    idcg = sum((2 ** gain - 1) / math.log2(index + 2) for index, gain in enumerate(ideal))
    return recall, dcg / idcg if idcg else 0.0


def evaluate(corpus_rows: list[tuple[str, str]], questions: list[dict], judged: dict[str, dict[str, int]], k: int) -> dict:
    indexed = [(key, tokens(content)) for key, content in corpus_rows]
    values = []
    for question in questions:
        if question["qid"] not in judged:
            continue
        query_tokens = tokens(question["query"])
        ranked = [key for key, _ in sorted(
            indexed,
            key=lambda item: (len(query_tokens & item[1]), len(query_tokens & item[1]) / max(len(query_tokens), 1)),
            reverse=True,
        )]
        values.append(metrics(question["query"], ranked, judged[question["qid"]], k))
    return {
        "queries": len(values),
        "k": k,
        "recall_at_k": round(sum(value[0] for value in values) / len(values), 6) if values else 0.0,
        "ndcg_at_k": round(sum(value[1] for value in values) / len(values), 6) if values else 0.0,
        "method": "lexical_proxy_not_pgvector",
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    questions_path = ROOT / "data/rag/evaluation/questions.jsonl"
    questions = [json.loads(line) for line in questions_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    judged = qrels(ROOT / "data/rag/evaluation/qrels.draft.tsv")
    rows = corpus(ROOT / "data/rag/processed/disease_knowledge.json", "disease_code")
    result = evaluate(rows, questions, judged, 4)
    output = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(output + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
