# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0

from collections import defaultdict
import hashlib
import json
import random

from features import REFUSE, predict
from noise import variants
from train import ROOT, SEED, jsonl, label_definitions, split_families

FIXTURE = ROOT.parents[1] / "assistant" / "test" / "fixtures" / "classifier.json"
KINDS = ("original", "no_accents", "telex", "repeat_or_drop")


def examples(task):
    rows = jsonl(ROOT / "data" / "seeds" / f"{task}.jsonl")
    rows.extend(row for row in jsonl(ROOT / "data" / "negatives.jsonl") if row["task"] == task)
    originals = split_families(rows, task)["train"]
    noise = random.Random(f"{SEED}:{task}:noise")
    buckets = defaultdict(list)
    for row in originals:
        buckets["original"].append(row)
        if "source" not in row:
            for variant in variants(row, noise):
                buckets[variant["noise"]].append(variant)
    rng = random.Random(f"{SEED}:{task}:parity")
    return [(kind, row) for kind in KINDS for row in rng.sample(buckets[kind], 13)]


def main():
    fixture = {}
    for task in label_definitions()["tasks"]:
        weights = (ROOT / "model" / f"{task}.json").read_bytes()
        model = json.loads(weights)
        cases = []
        for kind, row in examples(task):
            result = predict(model, row)
            cases.append({"id": row["id"], "kind": kind,
                          "inputs": [row["title"], row["text"]] if task == "support" else [row["text"]],
                          "label": None if result["label"] == REFUSE else result["label"],
                          "probability": result["confidence"], "evidence": result["evidence"]})
        fixture[task] = {"sha256": hashlib.sha256(weights).hexdigest(), "cases": cases}
    lines = ["{"]
    for index, (task, entry) in enumerate(fixture.items()):
        lines.extend([f'  "{task}": {{', f'    "sha256": "{entry["sha256"]}",', '    "cases": ['])
        lines.append(",\n".join("      " + json.dumps(case, ensure_ascii=False) for case in entry["cases"]))
        lines.extend(["    ]", "  }" + ("," if index + 1 < len(fixture) else "")])
    lines.append("}")
    FIXTURE.parent.mkdir(parents=True, exist_ok=True)
    FIXTURE.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
