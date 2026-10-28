# SPDX-License-Identifier: Apache-2.0
"""Measure scope decisions using saved labels; optionally run the real chatbot search."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from app.rag.scope_gate import classify_scope

ROOT = Path(__file__).resolve().parents[2] / "data/rag/evaluation"


def measure(path: Path, live: bool) -> dict:
    rows = [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
    if live:
        from sqlalchemy import create_engine, text
        from sqlalchemy.orm import Session

        from app.core.config import get_settings
        from app.rag.chat_search import ChatSearch

        if get_settings().chat_retrieval_strategy == "hybrid_rerank":
            from app.rag.reranker import _model

            _model()
        rows += [
            json.loads(line)
            for line in (ROOT / "no_evidence_questions.jsonl").read_text().splitlines()
            if line.strip()
        ]
        engine = create_engine(get_settings().database_url, hide_parameters=True)
        with engine.connect() as connection:
            if connection.execute(
                text(
                    "SELECT has_table_privilege(current_user, 'documents', 'INSERT,UPDATE,DELETE')"
                )
            ).scalar_one():
                raise ValueError("Scope evaluation requires a read-only role")
    records = []
    try:
        for row in rows:
            if live:
                with Session(engine) as db:
                    answer = ChatSearch().search(db, row["query"], row["namespace"])
                if answer["reason_code"] == "RERANK_UNAVAILABLE":
                    raise RuntimeError(
                        "Scope evaluation cannot score unavailable inference"
                    )
                actual = answer["scope_status"] != "OUT_OF_SCOPE"
            else:
                decision = classify_scope(row["query"], row["namespace"])
                actual = decision.retrieval_allowed
                answer = dict(
                    scope_status=decision.scope, reason_code=decision.reason_code
                )
            expected = row["label"] == "in_scope"
            records.append({**row, "actual": answer, "correct": actual == expected})
    finally:
        if live:
            engine.dispose()
    tp = sum(r["label"] == "in_scope" and r["correct"] for r in records)
    fn = sum(r["label"] == "in_scope" and not r["correct"] for r in records)
    tn = sum(r["label"] == "out_of_scope" and r["correct"] for r in records)
    fp = sum(r["label"] == "out_of_scope" and not r["correct"] for r in records)
    precision = tp / (tp + fp) if tp + fp else 0
    recall = tp / (tp + fn) if tp + fn else 0
    missing = [r for r in records if r.get("expected_status")]
    return dict(
        status="draft",
        boundary="real chatbot search" if live else "legacy rule gate only",
        count=len(records),
        tp=tp,
        fp=fp,
        tn=tn,
        fn=fn,
        precision=precision,
        recall=recall,
        f1=2 * precision * recall / (precision + recall) if precision + recall else 0,
        no_evidence_cases=len(missing),
        no_evidence_correct=sum(
            r["actual"]["scope_status"] == r["expected_status"] for r in missing
        ),
        records=records,
    )


def publish(path: Path) -> None:
    from evaluate_rag import outside_repository, read_json, write_json
    from langfuse import Evaluation, Langfuse

    path = outside_repository(path)
    data = read_json(path)
    receipt = path.with_suffix(".publish.json")
    if receipt.exists():
        return
    client = Langfuse()
    try:
        if not client.auth_check():
            raise RuntimeError("Langfuse authentication failed")

        def evaluate(*, output, **_):
            values = [Evaluation(name="scope_correct", value=int(output["correct"]))]
            if output.get("expected_status"):
                values.append(
                    Evaluation(
                        name="no_evidence_correct",
                        value=int(
                            output["actual"]["scope_status"]
                            == output["expected_status"]
                        ),
                    )
                )
            return values

        records = {r["qid"]: r for r in data["records"]}
        result = client.run_experiment(
            name="Chatbot scope and missing evidence",
            run_name=path.parent.name,
            experiment_id=Langfuse.create_trace_id(seed=f"scope:{path.parent.name}"),
            data=[
                dict(
                    input={k: r[k] for k in ("qid", "namespace", "query")},
                    expected_output={k: r.get(k) for k in ("label", "expected_status")},
                )
                for r in data["records"]
            ],
            task=lambda *, item, **_: records[item["input"]["qid"]],
            evaluators=[evaluate],
            max_concurrency=1,
            metadata={k: v for k, v in data.items() if k != "records"},
        )
        if len(result.item_results) != len(records):
            raise RuntimeError("Incomplete scope replay")
        client.flush()
        write_json(receipt, {"url": result.experiment_url})
    finally:
        client.shutdown()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "path", nargs="?", type=Path, default=ROOT / "scope_questions.jsonl"
    )
    parser.add_argument("--live", action="store_true")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--publish", type=Path)
    args = parser.parse_args()
    if args.publish:
        publish(args.publish)
        return
    result = measure(args.path, args.live)
    if args.output:
        from evaluate_rag import outside_repository, write_json

        write_json(outside_repository(args.output), result)
    print(
        json.dumps(
            {k: v for k, v in result.items() if k != "records"}, ensure_ascii=False
        )
    )


if __name__ == "__main__":
    main()
