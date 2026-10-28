# SPDX-License-Identifier: Apache-2.0
"""Collect, score and publish a retrieval baseline without changing the application."""

from __future__ import annotations

import argparse
import copy
import importlib.metadata
import json
import math
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

AI_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = AI_ROOT.parent
EVALUATION_ROOT = REPO_ROOT / "data/rag/evaluation"
BOUNDARY = "Retrieval ranking before Backend evidence merging; raw/filtered boundary recorded per run"
PROFILES = {
    "farm_qa": ("farm", 4, None),
    "lab_qa": ("lab", 4, None),
    "farm_diagnostic": ("farm", 3, "disease_knowledge"),
}
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))


def code_identity() -> str:
    """Fingerprint mounted evaluation/runtime sources into private run provenance."""
    import hashlib

    digest = hashlib.sha256()
    for directory in (AI_ROOT / "app", AI_ROOT / "scripts", AI_ROOT / "evaluation"):
        for path in sorted([*directory.rglob("*.py"), *directory.rglob("*.js")]):
            digest.update(str(path.relative_to(AI_ROOT)).encode())
            digest.update(path.read_bytes())
    return digest.hexdigest()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: Any) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def outside_repository(path: Path) -> Path:
    resolved = path.expanduser().resolve()
    if resolved.is_relative_to(REPO_ROOT):
        raise ValueError("Evaluation artifacts must be outside the repository")
    return resolved


def load_questions() -> list[dict[str, Any]]:
    questions = [
        json.loads(line)
        for line in (EVALUATION_ROOT / "questions.jsonl")
        .read_text(encoding="utf-8")
        .splitlines()
        if line.strip()
    ]
    qids: set[str] = set()
    for q in questions:
        if not isinstance(q.get("qid"), str) or not q["qid"] or q["qid"] in qids:
            raise ValueError("Missing or duplicate question ID")
        qids.add(q["qid"])
        if (
            q.get("group") not in PROFILES
            or q.get("namespace") != PROFILES[q["group"]][0]
        ):
            raise ValueError(f"Invalid question profile: {q['qid']}")
        if not isinstance(q.get("query"), str) or not q["query"].strip():
            raise ValueError(f"Empty query: {q['qid']}")
        if q["group"] == "farm_diagnostic":
            if q.get("disease_code") != q.get("document_code") or q["query"] != (
                f"Explain disease {q['document_code']} in detail"
            ):
                raise ValueError(
                    f"Diagnostic query does not match the workflow: {q['qid']}"
                )
        elif q.get("disease_code"):
            raise ValueError(f"QA pilot must search the whole namespace: {q['qid']}")
    metadata = read_json(EVALUATION_ROOT / "metadata.json")
    counts = {g: sum(q["group"] == g for q in questions) for g in PROFILES}
    if counts != metadata["scope"] or metadata["status"] not in {"draft", "reviewed"}:
        raise ValueError("Dataset counts or review status are invalid")
    if metadata["status"] == "reviewed" and not all(
        metadata.get(k) for k in ("reviewer", "reviewed_at", "review_scope")
    ):
        raise ValueError("Reviewed labels require a reviewer, date and review scope")
    return questions


def corpus_index() -> dict[tuple[str, str], dict[str, Any]]:
    from app.rag.chunker import chunk_text
    from app.rag.ingestion_service import load_corpus, selected_corpora

    index = {}
    for corpus in selected_corpora():
        namespace = corpus.namespace
        for item in load_corpus(corpus):
            key = (namespace, item[corpus.title_field])
            if key in index:
                raise ValueError(f"Duplicate corpus title: {key}")
            index[key] = {
                "code": item[corpus.code_field],
                "title": item[corpus.title_field],
                "version": corpus.version,
                "document_type": corpus.document_type,
                "namespace": namespace,
                "disease_code": item[corpus.code_field]
                if corpus.code_is_disease_code
                else None,
                "source_ids": item["provenance"]["source_ids"],
                "chunks": list(dict.fromkeys(chunk_text(item["content"]))),
            }
    return index


def document_key(namespace: str, code: str, index: int) -> str:
    return f"{namespace}/{code}/{index}"


def validate_snapshot(db: Any, expected: dict) -> None:
    from sqlalchemy import select
    from sqlalchemy.orm import joinedload

    from app.db.models.document import Document

    documents = (
        db.execute(
            select(Document)
            .options(joinedload(Document.chunks))
            .where(Document.namespace.in_({ns for ns, _ in expected}))
        )
        .unique()
        .scalars()
        .all()
    )
    actual = {(d.namespace, d.title): d for d in documents}
    if len(actual) != len(documents) or set(actual) != set(expected):
        raise ValueError("Database documents differ from the corpus")
    for key, doc in actual.items():
        ref = expected[key]
        if (
            doc.document_type,
            doc.version,
            doc.disease_code,
            (doc.source or "").split(","),
        ) != (
            ref["document_type"],
            ref["version"],
            ref["disease_code"],
            ref["source_ids"],
        ):
            raise ValueError(f"Document metadata mismatch: {key}")
        chunks = sorted(doc.chunks, key=lambda c: c.chunk_index)
        if [c.chunk_index for c in chunks] != list(range(len(ref["chunks"]))) or (
            [c.content for c in chunks] != ref["chunks"]
        ):
            raise ValueError(f"Chunk snapshot mismatch: {key}")
        if any(
            c.embedding is None
            or len(c.embedding) != 384
            or not all(math.isfinite(float(v)) for v in c.embedding)
            for c in chunks
        ):
            raise ValueError(f"Missing or invalid embeddings: {key}")


def collect(output: Path, ingest_record: Path, strategy: str | None = None) -> None:
    from langgraph.runtime import Runtime
    from sqlalchemy import create_engine
    from sqlalchemy.orm import Session

    from app.core.config import get_settings
    from app.graph.context import WorkflowRuntime
    from app.graph.nodes.retrieve_knowledge import retrieve_knowledge
    from app.rag.retriever import RAGRetriever

    output = outside_repository(output)
    if output.exists():
        raise ValueError("Use a new output directory for each collection")
    questions, expected = load_questions(), corpus_index()
    metadata = read_json(EVALUATION_ROOT / "metadata.json")
    settings, provenance = get_settings(), read_json(ingest_record)
    versions = {
        n: importlib.metadata.version(n)
        for n in (
            "torch",
            "sentence-transformers",
            "transformers",
            "SQLAlchemy",
            "pgvector",
            "rank-bm25",
        )
    }
    if (
        not settings.database_url
        or provenance.get("embedding_model") != settings.embedding_model
        or provenance.get("corpora") != metadata["corpora"]
        or provenance.get("runtime_versions") != versions
        or not all(provenance.get(k) for k in ("completed_at", "model_artifact"))
    ):
        raise ValueError("Ingestion provenance differs from the collection runtime")
    records = []
    engine = create_engine(
        settings.database_url, pool_pre_ping=True, isolation_level="REPEATABLE READ"
    )
    try:
        with engine.connect() as connection:
            if not connection.exec_driver_sql(
                "SELECT NOT has_table_privilege(current_user, 'documents', 'INSERT,UPDATE,DELETE')"
            ).scalar_one():
                raise ValueError(
                    "Collector requires a database role without write privileges"
                )
        with Session(engine) as db:
            validate_snapshot(db, expected)
        retriever = RAGRetriever()
        if strategy == "hybrid_rerank":
            from app.rag.reranker import _model

            _model()  # Match HTTP startup; cold loading is outside query inference time.
        for q in questions:
            _, top_k, kind = PROFILES[q["group"]]
            started = time.perf_counter()
            record = dict(
                qid=q["qid"],
                query=q["query"],
                group=q["group"],
                namespace=q["namespace"],
                top_k=top_k,
                document_type=kind,
                disease_code=q.get("disease_code"),
                results=[],
                error=None,
                started_at=datetime.now(timezone.utc).isoformat(),
            )
            try:
                with Session(engine) as db:

                    def retrieve(query, disease_code, k):
                        return retriever.retrieve(
                            db,
                            query,
                            top_k=k,
                            namespace=q["namespace"],
                            document_type=kind,
                            disease_code=disease_code,
                        )

                    if q["group"] == "farm_diagnostic":
                        state = retrieve_knowledge(
                            {"cv_result": {"disease_code": q["disease_code"]}},
                            Runtime(context=WorkflowRuntime(db=db, rag_tool=retrieve)),
                        )
                        if state["errors"]:
                            raise RuntimeError("Diagnostic retrieval failed")
                        retrieved = state["rag_context"]
                    elif strategy:
                        from app.rag.chat_search import ChatSearch

                        response = ChatSearch(strategy=strategy).search(
                            db, q["query"], q["namespace"], top_k
                        )
                        if response["reason_code"] == "RERANK_UNAVAILABLE":
                            raise RuntimeError("Reranker unavailable")
                        retrieved = response["passages"]
                        record.update(
                            {
                                key: response[key]
                                for key in (
                                    "scope_status",
                                    "reason_code",
                                    "strategy",
                                    "rankings",
                                    "timings_ms",
                                )
                            }
                        )
                    else:
                        retrieved = retrieve(q["query"], None, top_k)
                for rank, item in enumerate(retrieved, 1):
                    ref = expected[(q["namespace"], item["document_title"])]
                    record["results"].append(
                        {
                            "rank": rank,
                            "document_key": document_key(
                                q["namespace"], ref["code"], item["chunk_index"]
                            ),
                            **{
                                key: item[key]
                                for key in (
                                    "document_title",
                                    "chunk_index",
                                    "content",
                                    "similarity",
                                    "score",
                                    "source_ids",
                                )
                            },
                        }
                    )
            except Exception as exc:
                record["error"] = f"Retrieval failed ({type(exc).__name__})"
            record["elapsed_ms"] = (time.perf_counter() - started) * 1000
            records.append(record)
        with Session(engine) as db:
            validate_snapshot(db, expected)
    finally:
        engine.dispose()
    output.mkdir(parents=True)
    payload = dict(
        run_id=output.name,
        dataset=metadata,
        questions=questions,
        retrieval_boundary=BOUNDARY,
        ingestion=provenance,
        runtime_versions=versions,
        snapshot=list(expected.values()),
        records=records,
        strategy=strategy or "legacy_raw",
        content_policy="farm-medication-lines-v1" if strategy else "verbatim",
        code_identity=code_identity(),
        chat_runtime={
            k: v for k, v in settings.model_dump().items() if k.startswith("chat_")
        },
        chatbot_boundary="after scope/threshold, before Backend merging"
        if strategy
        else "raw legacy retriever",
    )
    write_json(output / "collect.json", payload)
    with (output / "run.trec").open("w", encoding="utf-8") as f:
        for rec in records:
            for row in rec["results"]:
                f.write(
                    f"{rec['qid']} Q0 {row['document_key']} {row['rank']} {rec['top_k'] - row['rank'] + 1} {strategy or 'legacy_raw'}\n"
                )
    if any(r["error"] for r in records):
        raise RuntimeError("Collection saved, but retrieval errors invalidate this run")
    print(
        f"Collected {len(records)} queries ({strategy or 'legacy_raw'}); artifacts: {output}"
    )


def validate_records(payload: dict, questions: list[dict]) -> dict[str, dict]:
    policy = payload.get("content_policy", "verbatim")
    if policy not in {"verbatim", "farm-medication-lines-v1"}:
        raise ValueError("Unknown evidence content policy")
    if (
        payload.get("dataset") != read_json(EVALUATION_ROOT / "metadata.json")
        or (payload.get("questions") != questions)
        or payload.get("retrieval_boundary") != BOUNDARY
    ):
        raise ValueError(
            "Artifact dataset, questions or retrieval boundary do not match"
        )
    if not isinstance(payload.get("run_id"), str) or not payload["run_id"]:
        raise ValueError("Artifact has no run ID")
    chunks = {}
    for doc in payload.get("snapshot", []):
        for index, content in enumerate(doc["chunks"]):
            key = document_key(doc["namespace"], doc["code"], index)
            if key in chunks:
                raise ValueError("Duplicate snapshot chunk")
            chunks[key] = (content, doc["title"], index, doc["source_ids"])
    if not chunks:
        raise ValueError("Artifact has no corpus snapshot")
    records = payload.get("records", [])
    if len(records) != len(questions) or {r.get("qid") for r in records} != {
        q["qid"] for q in questions
    }:
        raise ValueError("Artifact must contain every dataset question exactly once")
    by_qid = {r["qid"]: r for r in records}
    for q in questions:
        r = by_qid[q["qid"]]
        namespace, top_k, kind = PROFILES[q["group"]]
        if any(
            r.get(k) != q.get(k)
            for k in ("query", "namespace", "group", "disease_code")
        ) or (r.get("top_k"), r.get("document_type")) != (top_k, kind):
            raise ValueError(f"Question/profile mismatch: {q['qid']}")
        if r.get("error") or not isinstance(r.get("results"), list):
            raise ValueError(f"Invalid retrieval: {q['qid']}")
        results = r["results"]
        keys = [x["document_key"] for x in results]
        if (
            len(results) > top_k
            or len(keys) != len(set(keys))
            or ([x["rank"] for x in results] != list(range(1, len(results) + 1)))
        ):
            raise ValueError(f"Invalid ranks or duplicate chunks: {q['qid']}")
        for row in results:
            key = row["document_key"]
            expected_chunk = chunks.get(key)
            if expected_chunk and policy == "farm-medication-lines-v1" and q["group"] == "farm_qa":
                from app.rag.safety import redact_medication_lines

                expected_chunk = (redact_medication_lines(expected_chunk[0]), *expected_chunk[1:])
            if (
                key not in chunks
                or not key.startswith(namespace + "/")
                or (
                    row["content"],
                    row["document_title"],
                    row["chunk_index"],
                    row["source_ids"],
                )
                != expected_chunk
            ):
                raise ValueError(
                    f"Unknown, changed or cross-namespace chunk: {q['qid']}"
                )
            if q.get("disease_code") and not key.startswith(
                f"farm/{q['disease_code']}/"
            ):
                raise ValueError(f"Diagnostic disease filter mismatch: {q['qid']}")
            if any(not math.isfinite(row[k]) for k in ("score", "similarity")):
                raise ValueError(f"Non-finite score: {q['qid']}")
    return by_qid


def load_qrels(
    path: Path, payload: dict, questions: list[dict]
) -> list[tuple[str, str, int]]:
    valid = {
        document_key(d["namespace"], d["code"], i)
        for d in payload["snapshot"]
        for i in range(len(d["chunks"]))
    }
    by_qid = {q["qid"]: q for q in questions}
    rows, seen = [], set()
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        qid, _, key, label = line.split()
        relevance = int(label)
        if (
            qid not in by_qid
            or key not in valid
            or relevance not in {0, 1, 2}
            or (qid, key) in seen
        ):
            raise ValueError("Unknown, duplicate or invalid qrel")
        q = by_qid[qid]
        if not key.startswith(q["namespace"] + "/") or (
            q.get("disease_code") and not key.startswith(f"farm/{q['disease_code']}/")
        ):
            raise ValueError("Qrel is outside the question's retrieval scope")
        seen.add((qid, key))
        rows.append((qid, key, relevance))
    if {qid for qid, _, rel in rows if rel >= 1} != set(by_qid):
        raise ValueError("Every question must have at least one relevant qrel")
    if payload["dataset"]["status"] == "reviewed":
        for qid, q in by_qid.items():
            eligible = {
                k
                for k in valid
                if k.startswith(q["namespace"] + "/")
                and (
                    not q.get("disease_code")
                    or k.startswith(f"farm/{q['disease_code']}/")
                )
            }
            if {k for q, k in seen if q == qid} != eligible:
                raise ValueError(
                    "Reviewed qrels must explicitly judge every eligible chunk"
                )
    return rows


def calculate(payload: dict, qrel_rows: list[tuple[str, str, int]]) -> dict:
    import ir_measures as ir

    provider = ir.providers.registry["pytrec_eval"]
    groups = []
    for group, (_, k, _) in PROFILES.items():
        records = [r for r in payload["records"] if r["group"] == group]
        qids = {r["qid"] for r in records}
        measures = [ir.R(rel=1) @ k, ir.nDCG(gains={0: 0, 1: 1, 2: 2}) @ k]
        qrels = [ir.Qrel(q, d, rel) for q, d, rel in qrel_rows if q in qids]
        run = [
            ir.ScoredDoc(r["qid"], x["document_key"], k - x["rank"] + 1)
            for r in records
            for x in r["results"]
        ]
        values = list(provider.iter_calc(measures, qrels, run))
        per_query = {qid: {} for qid in sorted(qids)}
        for value in values:
            name = f"Recall@{k}" if value.measure == measures[0] else f"nDCG@{k}"
            per_query[value.query_id][name] = value.value
        if any(len(v) != 2 for v in per_query.values()):
            raise ValueError("Metric provider omitted a question")
        groups.append(
            {
                "group": group,
                "k": k,
                "queries": dict(sorted(per_query.items())),
                "macro_average": {
                    name: math.fsum(v[name] for v in per_query.values()) / len(qids)
                    for name in next(iter(per_query.values()))
                },
            }
        )
    return {
        "run_id": payload["run_id"],
        "strategy": payload.get("strategy", "legacy_raw"),
        "segments": {
            f"{group['group']}/{kind}/{split}": {
                metric: sum(
                    group["queries"][q["qid"]][metric]
                    for q in payload["questions"]
                    if q["group"] == group["group"]
                    and q.get("question_kind", "specific") == kind
                    and q.get("split", "regression") == split
                )
                / sum(
                    1
                    for q in payload["questions"]
                    if q["group"] == group["group"]
                    and q.get("question_kind", "specific") == kind
                    and q.get("split", "regression") == split
                )
                for metric in group["macro_average"]
            }
            for group in groups
            for kind, split in {
                (q.get("question_kind", "specific"), q.get("split", "regression"))
                for q in payload["questions"]
                if q["group"] == group["group"]
            }
        },
        "dataset": payload["dataset"],
        "official": payload["dataset"]["status"] == "reviewed",
        "groups": groups,
        "metric_definition": {
            "provider": "pytrec_eval",
            "ir_measures": importlib.metadata.version("ir-measures"),
            "boundary": BOUNDARY,
            "recall_relevance": 1,
            "ndcg_gains": {"0": 0, "1": 1, "2": 2},
        },
    }


def score(collect_path: Path, qrels_path: Path, output: Path) -> None:
    payload, questions = read_json(collect_path), load_questions()
    validate_records(payload, questions)
    rows = load_qrels(qrels_path, payload, questions)
    result = calculate(payload, rows)
    output = outside_repository(output)
    output.mkdir(parents=True, exist_ok=True)
    write_json(output / "score.json", result)
    (output / "qrels.trec").write_text(
        "".join(f"{q} 0 {d} {r}\n" for q, d, r in rows), encoding="utf-8"
    )
    print(
        json.dumps(
            {g["group"]: g["macro_average"] for g in result["groups"]},
            ensure_ascii=False,
        )
    )
    if not result["official"]:
        print("Draft labels: internal pilot only; not an independent evaluation.")


def publish(
    collect_path: Path, score_path: Path, qrels_path: Path, *, dry_run: bool = False
) -> None:
    payload, questions = read_json(collect_path), load_questions()
    records = validate_records(payload, questions)
    rows = load_qrels(qrels_path, payload, questions)
    scores = read_json(score_path)
    if scores != calculate(payload, rows):
        raise ValueError("Saved scores do not match this run and qrels")
    host = os.environ.get("LANGFUSE_BASE_URL", "http://localhost:3000").rstrip("/")
    if dry_run:
        print(
            f"Publish validation: PASS, {len(records)} items, 3 experiments; no network used."
        )
        return
    from langfuse import Evaluation, Langfuse

    receipt_path = outside_repository(collect_path.parent / "publish.json")
    if receipt_path.exists():
        receipt = read_json(receipt_path)
        if receipt["host"] != host or receipt["scores"] != scores:
            raise ValueError(
                "Publish receipt belongs to a different destination or score set"
            )
        print("This saved run is already published; no duplicate experiment created.")
        return
    client = Langfuse(
        base_url=host,
        public_key=os.environ.get("LANGFUSE_PUBLIC_KEY"),
        secret_key=os.environ.get("LANGFUSE_SECRET_KEY"),
        flush_at=1,
    )
    try:
        try:
            authenticated = client.auth_check()
        except Exception:
            raise RuntimeError(
                "Cannot authenticate with Langfuse; check the local host and credentials"
            ) from None
        if not authenticated:
            raise RuntimeError("Langfuse authentication failed")
        receipt = {"host": host, "scores": scores, "experiments": []}
        for group in scores["groups"]:
            dataset_name = (
                f"rag/{payload['dataset']['dataset_version']}/{group['group']}"
            )
            client.create_dataset(
                name=dataset_name,
                metadata={
                    "label_status": payload["dataset"]["status"],
                    "boundary": BOUNDARY,
                },
            )
            items = []
            for q in questions:
                if q["group"] != group["group"]:
                    continue
                qid = q["qid"]
                expected = [
                    {
                        "document_key": key,
                        "relevance": rel,
                        "content": next(
                            d["chunks"][int(key.rsplit("/", 1)[1])]
                            for d in payload["snapshot"]
                            if key.startswith(f"{d['namespace']}/{d['code']}/")
                        ),
                    }
                    for query_id, key, rel in rows
                    if query_id == qid
                ]
                items.append(
                    client.create_dataset_item(
                        dataset_name=dataset_name,
                        id=f"{payload['dataset']['dataset_version']}-{qid}",
                        input={"qid": qid, "query": q["query"]},
                        expected_output={"judgments": expected},
                        metadata={
                            "profile": group["group"],
                            "label_status": payload["dataset"]["status"],
                            "ingestion": payload["ingestion"],
                            "boundary": BOUNDARY,
                        },
                    )
                )

            def replay(*, item: Any, **_: Any) -> dict:
                return records[item.input["qid"]]

            def evaluate(*, input: dict, **_: Any) -> list:
                return [
                    Evaluation(
                        name=name,
                        value=value,
                        comment="ir_measures; saved-artifact replay",
                    )
                    for name, value in group["queries"][input["qid"]].items()
                ]

            result = client.run_experiment(
                name=f"RAG {payload.get('strategy', 'legacy_raw')}: {group['group']}",
                run_name=f"{payload['run_id']}/{group['group']}",
                experiment_id=Langfuse.create_trace_id(
                    seed=f"{payload['run_id']}:{group['group']}"
                ),
                data=items,
                task=replay,
                evaluators=[evaluate],
                max_concurrency=1,
                metadata={
                    "recording": "replay of saved retrieval, not a new retrieval call",
                    "strategy": payload.get("strategy", "legacy_raw"),
                    "chatbot_boundary": payload.get("chatbot_boundary", BOUNDARY),
                    "run_id": payload["run_id"],
                    "label_status": payload["dataset"]["status"],
                    "boundary": BOUNDARY,
                },
            )
            if len(result.item_results) != len(items) or any(
                len(r.evaluations) != 2 for r in result.item_results
            ):
                raise RuntimeError("Langfuse experiment did not evaluate every item")
            receipt["experiments"].append(
                {"group": group["group"], "url": result.experiment_url}
            )
        client.flush()
        write_json(receipt_path, receipt)
        for entry in receipt["experiments"]:
            print(entry["group"], entry["url"])
    finally:
        client.shutdown()


def self_check() -> None:
    import tempfile

    import ir_measures as ir

    questions = load_questions()
    measures = [ir.R @ 3, ir.nDCG(gains={0: 0, 1: 1, 2: 2}) @ 3]
    values = list(
        ir.providers.registry["pytrec_eval"].iter_calc(
            measures,
            [ir.Qrel("q", "a", 1), ir.Qrel("q", "b", 1)],
            [
                ir.ScoredDoc("q", "a", 3),
                ir.ScoredDoc("q", "x", 2),
                ir.ScoredDoc("q", "b", 1),
            ],
        )
    )
    actual = {v.measure: v.value for v in values}
    assert (
        actual[measures[0]] == 1
        and abs(actual[measures[1]] - 0.9197207891481876) < 1e-12
    )
    graded = list(
        ir.providers.registry["pytrec_eval"].iter_calc(
            measures,
            [ir.Qrel("q", "a", 2), ir.Qrel("q", "b", 1)],
            [
                ir.ScoredDoc("q", "b", 3),
                ir.ScoredDoc("q", "x", 2),
                ir.ScoredDoc("q", "a", 1),
            ],
        )
    )
    assert (
        abs(
            next(v.value for v in graded if v.measure == measures[1])
            - (2 / (2 + 1 / math.log2(3)))
        )
        < 1e-12
    )
    metadata = read_json(EVALUATION_ROOT / "metadata.json")
    snapshot = list(
        {
            (q["namespace"], q["document_code"]): {
                "namespace": q["namespace"],
                "code": q["document_code"],
                "title": "fixture",
                "chunks": ["fixture"],
                "source_ids": [],
            }
            for q in questions
        }.values()
    )
    fixture = {
        "run_id": "self-check",
        "dataset": metadata,
        "questions": questions,
        "retrieval_boundary": BOUNDARY,
        "snapshot": snapshot,
        "records": [
            {
                **q,
                "top_k": PROFILES[q["group"]][1],
                "document_type": PROFILES[q["group"]][2],
                "results": [],
                "error": None,
            }
            for q in questions
        ],
    }
    validate_records(fixture, questions)
    first_key = document_key(
        questions[0]["namespace"], questions[0]["document_code"], 0
    )
    low_score = copy.deepcopy(fixture)
    low_score["records"][0]["results"] = [
        {
            "document_key": first_key,
            "rank": 1,
            "content": "fixture",
            "document_title": "fixture",
            "chunk_index": 0,
            "source_ids": [],
            "score": 0.1,
            "similarity": 0.1,
        }
    ]
    validate_records(low_score, questions)
    sanitized = copy.deepcopy(low_score)
    sanitized["content_policy"] = "farm-medication-lines-v1"
    first_doc = next(
        d for d in sanitized["snapshot"]
        if document_key(d["namespace"], d["code"], 0) == first_key
    )
    first_doc["chunks"][0] = "Theo dõi ruộng.\nPhun thuốc theo liều."
    sanitized["records"][0]["results"][0]["content"] = "Theo dõi ruộng."
    validate_records(sanitized, questions)
    sanitized["records"][0]["results"][0]["content"] = "Nội dung không có trong nguồn."
    try:
        validate_records(sanitized, questions)
    except ValueError:
        pass
    else:
        raise AssertionError("Changed sanitized evidence was accepted")
    for mutate in (
        lambda p: p["records"].pop(),
        lambda p: p["records"].append(p["records"][0]),
        lambda p: p["records"][0].update(error="failed"),
        lambda p: p["records"][0].update(top_k=20),
        lambda p: p["records"][0].update(query="changed"),
    ):
        broken = copy.deepcopy(fixture)
        mutate(broken)
        try:
            validate_records(broken, questions)
        except ValueError:
            pass
        else:
            raise AssertionError("Invalid artifact was accepted")
    duplicate_chunk = copy.deepcopy(low_score)
    duplicate_chunk["records"][0]["results"] *= 2
    try:
        validate_records(duplicate_chunk, questions)
    except ValueError:
        pass
    else:
        raise AssertionError("Duplicate result chunk was accepted")
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / "qrels.trec"
        rows = [
            (q["qid"], document_key(q["namespace"], q["document_code"], 0), 1)
            for q in questions
        ]
        path.write_text("".join(f"{q} 0 {key} {rel}\n" for q, key, rel in rows))
        load_qrels(path, fixture, questions)
        assert all(
            value == 0
            for group in calculate(fixture, rows)["groups"]
            for value in group["macro_average"].values()
        )
        for bad in (
            f"{rows[0][0]} 0 unknown/chunk/0 2\n",
            f"{rows[0][0]} 0 {rows[0][1]} 3\n",
            path.read_text().splitlines()[0] + "\n",
        ):
            path.write_text(path.read_text() + bad)
            try:
                load_qrels(path, fixture, questions)
            except ValueError:
                pass
            else:
                raise AssertionError("Invalid qrel was accepted")
            path.write_text("".join(f"{q} 0 {key} {rel}\n" for q, key, rel in rows))
    try:
        outside_repository(REPO_ROOT / "rag-evaluation-output")
    except ValueError:
        pass
    else:
        raise AssertionError("Repository output was accepted")
    print(
        "Self-check: PASS (binary/graded metrics, empty results, qrels, completeness, ranks, profiles, errors, output boundary)"
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "command", choices=("collect", "score", "publish", "self-check")
    )
    parser.add_argument(
        "--output", type=Path, help="Required output directory outside the repository"
    )
    parser.add_argument("--collect", type=Path)
    parser.add_argument(
        "--qrels", type=Path, default=EVALUATION_ROOT / "qrels.draft.tsv"
    )
    parser.add_argument("--score-file", type=Path)
    parser.add_argument("--ingest-record", type=Path)
    parser.add_argument(
        "--strategy", choices=("legacy", "bm25", "hybrid", "hybrid_rerank")
    )
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if args.command == "self-check":
        self_check()
    elif args.command == "collect":
        if not args.output or not args.ingest_record:
            parser.error("collect requires --output and --ingest-record")
        collect(args.output, args.ingest_record, args.strategy)
    elif args.command == "score":
        if not args.collect or not args.output:
            parser.error("score requires --collect and --output")
        score(args.collect, args.qrels, args.output)
    else:
        if not args.collect or not args.score_file:
            parser.error("publish requires --collect and --score-file")
        publish(args.collect, args.score_file, args.qrels, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
