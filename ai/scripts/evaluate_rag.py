# SPDX-License-Identifier: Apache-2.0
"""Collect and score the Phase 1 RAG retrieval baseline.

The collector reads the production vector database and calls the production
retriever. It never ingests, seeds, migrates, or writes to that database.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Any

AI_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = AI_ROOT.parent
EVALUATION_ROOT = REPO_ROOT / "data" / "rag" / "evaluation"
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))

def load_questions() -> list[dict[str, Any]]:
    questions = [
        json.loads(line)
        for line in (EVALUATION_ROOT / "questions.jsonl").read_text(
            encoding="utf-8"
        ).splitlines()
        if line.strip()
    ]
    qids = [item.get("qid") for item in questions]
    if len(qids) != len(set(qids)) or any(not qid for qid in qids):
        raise ValueError("questions.jsonl contains missing or duplicate qid values")
    return questions


def corpus_index() -> dict[tuple[str, str], dict[str, Any]]:
    from app.rag.chunker import chunk_text
    from app.rag.ingestion_service import CORPORA, load_corpus

    index: dict[tuple[str, str], dict[str, Any]] = {}
    for namespace, corpus in CORPORA.items():
        for item in load_corpus(corpus):
            code = item[corpus.code_field]
            title = item[corpus.title_field]
            key = (namespace, title)
            if key in index:
                raise ValueError(f"duplicate corpus title: {namespace}/{title}")
            index[key] = {
                "code": code,
                "title": title,
                "version": corpus.version,
                "document_type": corpus.document_type,
                "namespace": namespace,
                "source_ids": item["provenance"]["source_ids"],
                "chunks": list(dict.fromkeys(chunk_text(item["content"]))),
            }
    return index


def validate_snapshot(db: Any, expected: dict[tuple[str, str], dict[str, Any]]) -> dict[str, str]:
    from sqlalchemy import select
    from sqlalchemy.orm import joinedload

    from app.db.models.document import Document

    rows = db.execute(
        select(Document)
        .options(joinedload(Document.chunks))
        .where(Document.namespace.in_(sorted(CORPORA)))
    ).unique().scalars().all()
    actual: dict[tuple[str, str], Document] = {}
    for document in rows:
        key = (document.namespace, document.title)
        if key in actual:
            raise ValueError(f"duplicate database document: {key}")
        actual[key] = document

    if set(actual) != set(expected):
        missing = sorted(set(expected) - set(actual))
        extra = sorted(set(actual) - set(expected))
        raise ValueError(f"corpus snapshot mismatch; missing={missing}, extra={extra}")

    document_codes: dict[str, str] = {}
    for key, document in actual.items():
        reference = expected[key]
        if (
            document.document_type != reference["document_type"]
            or document.version != reference["version"]
            or document.source.split(",") != reference["source_ids"]
        ):
            raise ValueError(f"document metadata mismatch: {key}")
        chunks = sorted(document.chunks, key=lambda chunk: chunk.chunk_index)
        if [chunk.chunk_index for chunk in chunks] != list(range(len(chunks))):
            raise ValueError(f"chunk indexes are not contiguous: {key}")
        if [chunk.content for chunk in chunks] != reference["chunks"]:
            raise ValueError(f"chunk content mismatch: {key}")
        document_codes[str(document.id)] = reference["code"]
    return document_codes


def document_key(namespace: str, code: str, chunk_index: int) -> str:
    return f"{namespace}/{code}/{chunk_index}"


def collect(output: Path) -> None:
    from sqlalchemy import create_engine

    from app.core.config import get_settings
    from app.rag.retriever import RAGRetriever

    from app.rag.ingestion_service import CORPORA

    questions = load_questions()
    expected = corpus_index()
    settings = get_settings()
    if not settings.database_url:
        raise RuntimeError("DATABASE_URL is not configured")

    engine = create_engine(settings.database_url, pool_pre_ping=True)
    records: list[dict[str, Any]] = []
    try:
        with engine.connect() as connection:
            with connection.begin():
                connection.exec_driver_sql("SET TRANSACTION READ ONLY")
                db = Session(bind=connection)
                try:
                    validate_snapshot(db, expected)
                    retriever = RAGRetriever()
                    for question in questions:
                        group = question["group"]
                        top_k = 3 if group == "farm_diagnostic" else 4
                        document_type = None if group in {"farm_qa", "lab_qa"} else "disease_knowledge"
                        started = time.perf_counter()
                        error: str | None = None
                        results: list[dict[str, Any]] = []
                        try:
                            retrieved = retriever.retrieve(
                                db=db,
                                query=question["query"],
                                top_k=top_k,
                                document_type=document_type,
                                disease_code=question.get("disease_code"),
                                namespace=question["namespace"],
                            )
                            for rank, item in enumerate(retrieved, start=1):
                                reference = expected.get(
                                    (question["namespace"], item["document_title"])
                                )
                                code = item.get("disease_code") or (
                                    reference or {}
                                ).get("code")
                                if not code:
                                    raise ValueError(
                                        f"cannot map result to stable document code for {question['qid']}"
                                    )
                                results.append(
                                    {
                                        "rank": rank,
                                        "document_key": document_key(
                                            question["namespace"], code, item["chunk_index"]
                                        ),
                                        "document_title": item["document_title"],
                                        "chunk_index": item["chunk_index"],
                                        "content": item["content"],
                                        "similarity": item["similarity"],
                                        "score": item["score"],
                                        "source_ids": item["source_ids"],
                                    }
                                )
                        except Exception as exc:  # keep a record for every question
                            error = f"{type(exc).__name__}: {exc}"
                        records.append(
                            {
                                "qid": question["qid"],
                                "query": question["query"],
                                "group": group,
                                "namespace": question["namespace"],
                                "top_k": top_k,
                                "document_type": document_type,
                                "disease_code": question.get("disease_code"),
                                "elapsed_ms": round((time.perf_counter() - started) * 1000, 2),
                                "results": results,
                                "error": error,
                            }
                        )
                finally:
                    db.close()
    finally:
        engine.dispose()

    output.mkdir(parents=True, exist_ok=True)
    payload = {
        "dataset_version": json.loads(
            (EVALUATION_ROOT / "metadata.json").read_text(encoding="utf-8")
        )["dataset_version"],
        "retrieval_boundary": "RAGRetriever output before Backend filtering and evidence merging",
        "records": records,
    }
    (output / "collect.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    with (output / "run.trec").open("w", encoding="utf-8") as run_file:
        for record in records:
            for result in record["results"]:
                rank = result["rank"]
                run_file.write(
                    f"{record['qid']} Q0 {result['document_key']} "
                    f"{rank} {record['top_k'] - rank + 1} phase-1\n"
                )
    errors = [record for record in records if record["error"]]
    if errors:
        raise RuntimeError(
            f"collection written to {output}, but {len(errors)} question(s) failed"
        )


def self_check() -> None:
    import ir_measures

    questions = load_questions()
    metadata = json.loads((EVALUATION_ROOT / "metadata.json").read_text(encoding="utf-8"))
    if metadata["status"] not in {"draft", "reviewed"}:
        raise ValueError("metadata.status must be draft or reviewed")
    groups = {group: sum(item["group"] == group for item in questions) for group in {
        "farm_qa", "lab_qa", "farm_diagnostic"
    }}
    if groups != {"farm_qa": 12, "lab_qa": 24, "farm_diagnostic": 6}:
        raise ValueError(f"unexpected question groups: {groups}")
    qrels = [ir_measures.Qrel("self-check", "relevant-1", 1), ir_measures.Qrel("self-check", "relevant-2", 1)]
    run = [
        ir_measures.ScoredDoc("self-check", "relevant-1", 3),
        ir_measures.ScoredDoc("self-check", "irrelevant", 2),
        ir_measures.ScoredDoc("self-check", "relevant-2", 1),
    ]
    values = list(
        ir_measures.iter_calc(
            [ir_measures.R @ 3, ir_measures.nDCG(gains={0: 0, 1: 1, 2: 2}) @ 3],
            qrels,
            run,
        )
    )
    metric_values = {str(value.measure): value.value for value in values}
    if metric_values.get("R@3") != 1.0 or abs(metric_values["nDCG(gains={})@3"] - 0.9197207891481876) > 1e-12:
        raise ValueError(f"unexpected metric self-check values: {metric_values}")
    print("RAG evaluation self-check: PASS")


def score(collect_path: Path, qrels_path: Path, output: Path) -> None:
    """Score a saved run without loading an embedding model or opening a DB."""
    payload = json.loads(collect_path.read_text(encoding="utf-8"))
    records = payload.get("records")
    if not isinstance(records, list) or not records:
        raise ValueError("collect artifact has no records")
    qids = [record.get("qid") for record in records]
    if any(not qid for qid in qids) or len(qids) != len(set(qids)):
        raise ValueError("collect artifact has missing or duplicate qids")
    failures = [record["qid"] for record in records if record.get("error")]
    if failures:
        raise ValueError(f"cannot score failed retrievals: {failures}")

    qrel_rows: list[tuple[str, str, int]] = []
    seen_qrels: set[tuple[str, str]] = set()
    for line in qrels_path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        parts = line.split()
        if len(parts) != 4:
            raise ValueError(f"invalid qrels row: {line}")
        qid, _iteration, doc_id, relevance_text = parts
        relevance = int(relevance_text)
        if relevance not in {0, 1, 2}:
            raise ValueError(f"invalid relevance in qrels row: {line}")
        key = (qid, doc_id)
        if key in seen_qrels:
            raise ValueError(f"duplicate qrels row: {key}")
        seen_qrels.add(key)
        qrel_rows.append((qid, doc_id, relevance))
    if {row[0] for row in qrel_rows} != set(qids):
        raise ValueError("qrels qids do not match collect artifact")

    import ir_measures

    qrels = [
        ir_measures.Qrel(qid, doc_id, relevance)
        for qid, doc_id, relevance in qrel_rows
    ]
    grouped: dict[tuple[str, int], list[dict[str, Any]]] = {}
    for record in records:
        grouped.setdefault((record["group"], record["top_k"]), []).append(record)

    score_groups: list[dict[str, Any]] = []
    for (group, top_k), group_records in sorted(grouped.items()):
        group_qids = {record["qid"] for record in group_records}
        run = [
            ir_measures.ScoredDoc(
                record["qid"],
                result["document_key"],
                top_k - result["rank"] + 1,
            )
            for record in group_records
            for result in record["results"]
        ]
        group_qrels = [row for row in qrels if row.query_id in group_qids]
        measures = [
            ir_measures.R @ top_k,
            ir_measures.nDCG(gains={0: 0, 1: 1, 2: 2}) @ top_k,
        ]
        values = list(ir_measures.iter_calc(measures, group_qrels, run))
        per_query: dict[str, dict[str, float]] = {
            qid: {} for qid in sorted(group_qids)
        }
        for value in values:
            per_query[value.query_id][str(value.measure)] = value.value
        if any(len(metrics) != 2 for metrics in per_query.values()):
            raise ValueError(f"metric output missing a query in group {group}")
        macro = {
            metric: sum(metrics[metric] for metrics in per_query.values())
            / len(per_query)
            for metric in next(iter(per_query.values()))
        }
        score_groups.append(
            {
                "group": group,
                "k": top_k,
                "queries": per_query,
                "macro_average": macro,
            }
        )

    metadata = json.loads((EVALUATION_ROOT / "metadata.json").read_text(encoding="utf-8"))
    output.mkdir(parents=True, exist_ok=True)
    (output / "score.json").write_text(
        json.dumps(
            {
                "dataset_version": payload.get("dataset_version"),
                "label_status": metadata["status"],
                "official": metadata["status"] == "reviewed",
                "metric_definition": {
                    "recall": "Recall@k",
                    "ndcg": "nDCG@k with linear gain {0: 0, 1: 1, 2: 2}",
                    "boundary": "RAGRetriever output before Backend filtering and evidence merging",
                },
                "groups": score_groups,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("collect", "score", "self-check"))
    parser.add_argument("--output", type=Path, default=REPO_ROOT / "rag-evaluation-output")
    parser.add_argument(
        "--collect",
        type=Path,
        default=REPO_ROOT / "rag-evaluation-output" / "collect.json",
        help="Saved collect.json used by score.",
    )
    parser.add_argument(
        "--qrels",
        type=Path,
        default=EVALUATION_ROOT / "qrels.draft.tsv",
        help="TREC qrels used by score.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.command == "self-check":
        self_check()
    elif args.command == "score":
        score(args.collect, args.qrels, args.output)
    else:
        collect(args.output)


if __name__ == "__main__":
    main()
