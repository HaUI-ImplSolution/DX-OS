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
    questions = load_questions()
    metadata = json.loads((EVALUATION_ROOT / "metadata.json").read_text(encoding="utf-8"))
    if metadata["status"] not in {"draft", "reviewed"}:
        raise ValueError("metadata.status must be draft or reviewed")
    groups = {group: sum(item["group"] == group for item in questions) for group in {
        "farm_qa", "lab_qa", "farm_diagnostic"
    }}
    if groups != {"farm_qa": 12, "lab_qa": 24, "farm_diagnostic": 6}:
        raise ValueError(f"unexpected question groups: {groups}")
    print("RAG evaluation self-check: PASS")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("collect", "self-check"))
    parser.add_argument("--output", type=Path, default=REPO_ROOT / "rag-evaluation-output")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.command == "self-check":
        self_check()
    else:
        collect(args.output)


if __name__ == "__main__":
    main()
