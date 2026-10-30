# SPDX-License-Identifier: AGPL-3.0-only
"""Prepare evaluation-only data, validate saved trials and serve readiness probes."""

from __future__ import annotations

import argparse
import importlib.metadata
import json
import os
import platform
import re
from datetime import datetime, timezone
from pathlib import Path


def read(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def write(path: Path, value) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def versions() -> dict[str, str]:
    return {
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


def identifier(value: str) -> str:
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]{0,62}", value):
        raise ValueError("Invalid evaluation database/role identifier")
    return '"' + value + '"'


def prepare() -> None:
    from evaluate_rag import code_identity, corpus_index, validate_snapshot
    from huggingface_hub import snapshot_download
    from psycopg import sql
    from sqlalchemy import create_engine, text
    from sqlalchemy.orm import Session

    from app.core.config import get_settings
    from app.db.base import Base
    from app.db.models import document, document_chunk  # noqa: F401
    from app.rag.ingestion_service import CORPORA, ingest_corpus, selected_corpora

    cache = Path("/cache")
    model = cache / "model"
    source = os.environ["EVAL_EMBEDDING_MODEL"]
    if model.is_symlink() or model.exists():
        if not model.is_dir() or read(cache / "model-source.json")["source"] != source:
            raise ValueError(
                "Cached model source mismatch; use a separate evaluation state"
            )
    else:
        snapshot = Path(
            snapshot_download(
                source,
                allow_patterns=["*.json", "*.txt", "*.safetensors", "1_Pooling/*"],
            )
        )
        model.symlink_to(snapshot, target_is_directory=True)
        write(
            cache / "model-source.json", {"source": source, "snapshot": str(snapshot)}
        )
    reranker = cache / "reranker"
    reranker_source = os.environ["EVAL_RERANKER_MODEL"]
    if reranker.exists():
        if read(cache / "reranker-source.json")["source"] != reranker_source:
            raise ValueError(
                "Cached reranker source mismatch; use separate evaluation state"
            )
    else:
        snapshot = Path(
            snapshot_download(
                reranker_source,
                allow_patterns=["*.json", "*.txt", "*.model", "*.safetensors"],
                ignore_patterns=["onnx/*", "openvino/*"],
            )
        )
        reranker.symlink_to(snapshot, target_is_directory=True)
        write(
            cache / "reranker-source.json",
            {"source": reranker_source, "snapshot": str(snapshot)},
        )
    # All consumers now use the same immutable local snapshot, without a remote check.
    os.environ["HF_HUB_OFFLINE"] = "1"
    output = Path("/artifacts") / os.environ["EVAL_RUN"] / "ingest.json"
    expected = corpus_index()
    signature = {
        "source_model": read(cache / "model-source.json"),
        "reranker": read(cache / "reranker-source.json"),
        "chat_profiles": read(Path("/workspace/data/rag/chat_profiles.json")),
        "chat_runtime": {
            k: v
            for k, v in get_settings().model_dump().items()
            if k.startswith("chat_") and k != "chat_retrieval_strategy"
        },
        "runtime_versions": versions(),
        "corpora": {k: c.version for k, c in CORPORA.items()},
        "snapshot": [[list(k), v] for k, v in sorted(expected.items())],
    }
    previous = cache / "ingestion.json"
    engine = create_engine(
        os.environ["EVAL_ADMIN_DATABASE_URL"], pool_pre_ping=True, hide_parameters=True
    )
    role = os.environ["EVAL_READONLY_USER"]
    role_sql, db_sql = identifier(role), identifier(os.environ["EVAL_POSTGRES_DB"])
    with engine.begin() as conn:
        conn.exec_driver_sql("CREATE EXTENSION IF NOT EXISTS vector")
    Base.metadata.create_all(engine)
    with engine.begin() as conn:
        exists = conn.execute(
            text("SELECT 1 FROM pg_roles WHERE rolname=:role"), {"role": role}
        ).scalar()
        with conn.connection.driver_connection.cursor() as cursor:
            cursor.execute(
                sql.SQL(
                    "ALTER ROLE {} LOGIN PASSWORD {}"
                    if exists
                    else "CREATE ROLE {} LOGIN PASSWORD {}"
                ).format(
                    sql.Identifier(role),
                    sql.Literal(os.environ["EVAL_READONLY_PASSWORD"]),
                )
            )
        conn.exec_driver_sql(f"GRANT CONNECT ON DATABASE {db_sql} TO {role_sql}")
        conn.exec_driver_sql(f"GRANT USAGE ON SCHEMA public TO {role_sql}")
        conn.exec_driver_sql(
            f"GRANT SELECT ON ALL TABLES IN SCHEMA public TO {role_sql}"
        )
        conn.exec_driver_sql(
            f"ALTER ROLE {role_sql} SET default_transaction_read_only = on"
        )
    refresh = not previous.exists() or read(previous) != signature
    if not refresh:
        with Session(engine) as db:
            try:
                validate_snapshot(db, expected)
            except ValueError:
                refresh = True
    if refresh:
        with Session(engine) as db:
            for corpus in selected_corpora():
                ingest_corpus(db, corpus)
        write(previous, signature)
    with Session(engine) as db:
        validate_snapshot(db, expected)
    # Verify database privileges, not merely the default read-only setting.
    with create_engine(os.environ["DATABASE_URL"]).connect() as conn:
        if (
            conn.exec_driver_sql("SHOW default_transaction_read_only").scalar_one()
            != "on"
        ):
            raise ValueError("Evaluation role must default to read-only")
        if conn.exec_driver_sql(
            "SELECT has_table_privilege(current_user, 'documents', 'INSERT,UPDATE,DELETE')"
        ).scalar_one():
            raise ValueError("Evaluation role has write privileges")
    write(
        output,
        {
            "code_identity": code_identity(),
            "embedding_model": str(model),
            "source_model": source,
            "reranker": signature["reranker"],
            "chat_profiles": signature["chat_profiles"],
            "chat_runtime": signature["chat_runtime"],
            "model_artifact": str(model.resolve()),
            "corpora": signature["corpora"],
            "runtime_versions": signature["runtime_versions"],
            "python": platform.python_version(),
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "documents": len(expected),
            "chunks": sum(len(d["chunks"]) for d in expected.values()),
            "refreshed": refresh,
        },
    )
    print(f"Prepared {len(expected)} documents; refreshed={refresh}", flush=True)


def check_benchmark(path: Path) -> None:
    from benchmark_rag import summarize

    data = read(path / "benchmark.json")
    expected = {
        (ns, vu, rep)
        for ns in ("farm", "lab")
        for vu in (1, 2, 5)
        for rep in range(1, data["repeats"] + 1)
    }
    if (
        not data.get("completed_at")
        or len(data["trials"]) != data["repeats"] * 6
        or {(t["namespace"], t["vus"], t["repeat"]) for t in data["trials"]} != expected
    ):
        raise ValueError("Incomplete benchmark; artifacts retained")
    failed = False
    for trial in data["trials"]:
        folder = path / trial["directory"]
        if folder.parent != path or not (folder / "dashboard.html").is_file():
            raise ValueError("Missing native k6 HTML report")
        if any(
            trial[k] != v
            for k, v in summarize(
                folder, [c["name"] for c in data["containers"]]
            ).items()
        ):
            raise ValueError("Summary differs from raw benchmark artifacts")
        failed |= (
            trial["k6_exit_code"] != 0
            or trial["http_failure_rate"] > 0
            or trial["invalid_response_rate"] > 0
        )
    if failed:
        raise ValueError(
            "Benchmark contains failed trials/responses; artifacts retained"
        )
    print(f"Verified {len(data['trials'])} trials / {sum(t['requests'] for t in data['trials'])} requests")


def self_check() -> None:
    assert identifier("eval_ro") == '"eval_ro"'
    for value in ("", "bad-name", 'x"', "x; DROP ROLE eval_ro", "a" * 64):
        try:
            identifier(value)
        except ValueError:
            continue
        raise AssertionError("Invalid identifier accepted")
    print("prepare self-check: PASS")


def compare_quality(root: Path) -> None:
    import csv
    import html

    variants = ("legacy", "bm25", "hybrid", "hybrid_rerank")
    datasets, rows = [], []
    for strategy in variants:
        matches = list(root.glob(f"quality-{strategy}-*/score.json"))
        if len(matches) != 1:
            raise ValueError("Expected exactly one score file per strategy")
        result = read(matches[0])
        payload = read(matches[0].parent / "collect.json")
        datasets.append((payload["dataset"], payload["snapshot"], payload["questions"]))
        if datasets[-1] != datasets[0]:
            raise ValueError("Cannot compare different datasets or corpus snapshots")
        for group in result["groups"]:
            rows.append(
                dict(
                    strategy=strategy,
                    group=group["group"],
                    k=group["k"],
                    recall=group["macro_average"][f"Recall@{group['k']}"],
                    ndcg=group["macro_average"][f"nDCG@{group['k']}"],
                )
            )
    write(root / "comparison.json", {"status": datasets[0][0]["status"], "rows": rows})
    with (root / "comparison.csv").open("w") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    table = "".join(
        "<tr>"
        + "".join("<td>" + html.escape(str(v)) + "</td>" for v in row.values())
        + "</tr>"
        for row in rows
    )
    (root / "comparison.html").write_text(
        '<!doctype html><meta charset="utf-8"><title>RAG comparison</title><style>body{font:16px system-ui;margin:32px}td,th{padding:10px;border:1px solid #ccc}table{border-collapse:collapse}</style><h1>RAG strategy comparison</h1><p>Dataset status: '
        + html.escape(datasets[0][0]["status"])
        + ". Scores measure retrieval, not generated-answer accuracy.</p><table><thead><tr><th>Strategy</th><th>Group</th><th>k</th><th>Recall</th><th>nDCG</th></tr></thead><tbody>"
        + table
        + "</tbody></table>"
    )
    print(
        "Strategy comparison: comparison.json / comparison.csv / comparison.html",
        flush=True,
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "command",
        choices=("prepare", "check-benchmark", "compare-quality", "self-check"),
    )
    parser.add_argument("values", nargs="*")
    args = parser.parse_args()
    if args.command == "prepare":
        prepare()
    elif args.command == "check-benchmark":
        check_benchmark(Path(args.values[0]))
    elif args.command == "compare-quality":
        compare_quality(Path(args.values[0]))
    else:
        self_check()
