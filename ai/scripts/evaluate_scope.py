# SPDX-License-Identifier: Apache-2.0
"""Compute scope-gate precision/recall/F1 from a labelled JSONL set."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from app.rag.scope_gate import classify_scope


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "path", nargs="?", type=Path,
        default=Path(__file__).resolve().parents[2] / "data/rag/evaluation/scope_questions.jsonl",
    )
    args = parser.parse_args()
    rows = [json.loads(line) for line in args.path.read_text(encoding="utf-8").splitlines() if line.strip()]
    tp = fp = fn = tn = 0
    by_namespace: dict[str, dict[str, int]] = {}
    for row in rows:
        expected = row["label"] == "in_scope"
        actual = classify_scope(row["query"], row["namespace"]).retrieval_allowed
        if expected and actual:
            tp += 1
        elif expected:
            fn += 1
        elif actual:
            fp += 1
        else:
            tn += 1
        stats = by_namespace.setdefault(row["namespace"], {"total": 0, "correct": 0})
        stats["total"] += 1
        stats["correct"] += int(expected == actual)
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    print(json.dumps({
        "dataset": str(args.path), "count": len(rows), "tp": tp, "fp": fp,
        "fn": fn, "tn": tn, "precision": round(precision, 4),
        "recall": round(recall, 4), "f1": round(f1, 4),
        "accuracy_by_namespace": by_namespace,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
