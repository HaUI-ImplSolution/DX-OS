# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0

import argparse
from collections import Counter, defaultdict
import csv
from functools import lru_cache
import hashlib
from io import StringIO
from itertools import groupby
import json
import os
from pathlib import Path
import random
import tarfile
import tempfile

from features import (
    FEATURES, FORMAT_VERSION, REFUSE, command_pattern, evidence, extract, input_fields,
    normalized, probabilities, rank,
)
from fetch import CACHE, ROOT, sources, verify
from noise import variants

SEED = 210
EPOCHS = 28
LEARNING_RATE = 0.22
FEATURE_GRID = {
    "balanced": FEATURES,
    "folded_words": {
        **FEATURES, "character_range": [3, 5], "fold_words": True,
        "scales": {"accented": 0.65, "unaccented": 0.9, "word": 3.2},
    },
    "short_ngrams": {
        **FEATURES, "character_range": [2, 4], "fold_words": True,
        "scales": {"accented": 0.8, "unaccented": 1.0, "word": 2.0},
    },
}
L2_GRID = (0.00012, 0.00004)
TARGET_PERCENT = 97
REFUSAL_CASES = ("lac_de", "mo_ho", "hai_y", "chen_lenh")
EXTERNAL_SAMPLE_SIZE = 300
SPLITS = ("train", "calibration", "dev")


def jsonl(path):
    with path.open(encoding="utf-8") as stream:
        return [json.loads(line) for line in stream if line.strip()]


def read_input(path, task, definitions, content=None):
    suffix = path.suffix.lower()
    if suffix == ".csv":
        if content is None:
            content = path.read_bytes()
        raw = list(csv.DictReader(StringIO(content.decode("utf-8-sig"), newline="")))
    elif suffix == ".jsonl":
        raw = jsonl(path)
    else:
        raise ValueError("Input must be a .csv or .jsonl file")

    rows = []
    ids = set()
    labels = {*definitions["labels"], REFUSE}
    aliases = {
        "id": "id", "text": "van_ban", "label": "nhan",
        "case": "loai_ca", "style": "van_phong",
    }
    for number, entry in enumerate(raw, 1):
        if not isinstance(entry, dict) or entry.get("task", task) not in (task, "*"):
            raise ValueError(f"Row {number}: invalid row or task")
        row = {key: entry.get(key, entry.get(alias)) for key, alias in aliases.items()}
        if task == "support":
            row["title"] = entry.get("title", entry.get("tieu_de", ""))
        if (any(not isinstance(value, str) for value in row.values())
                or not row["id"] or not row["case"] or not row["style"]
                or row["label"] not in labels or row["id"] in ids):
            raise ValueError(f"Row {number}: invalid fields, label or duplicate id")
        ids.add(row["id"])
        rows.append(row)
    if not rows:
        raise ValueError("The input has no records")
    return rows


def label_definitions():
    return json.loads((ROOT / "labels.json").read_text(encoding="utf-8"))


def encode(row):
    return json.dumps(row, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def pick_external(name, candidates):
    unique = {}
    for source_id, text in sorted(candidates):
        key = normalized(text)
        if key:
            unique.setdefault(key, (source_id, text))
    sample = random.Random(f"{SEED}:{name}").sample(list(unique.values()), EXTERNAL_SAMPLE_SIZE)
    return [
        dict(id=f"{name}-{source_id}", task="*", text=text, label=REFUSE, case="lac_de",
             style="nguon_ngoai", family=f"{name}-{source_id}", source=name)
        for source_id, text in sample
    ]


@lru_cache(maxsize=1)
def external_rows():
    manifest = sources()
    massive = manifest["massive"]
    archive_path = verify(CACHE / massive["filename"], massive["sha256"])
    excluded = set(massive["excluded_ids"])
    with tarfile.open(archive_path, "r:gz") as archive:
        with archive.extractfile(archive.getmember(massive["member"])) as entries:
            massive_rows = [
                (row["id"], row["utt"]) for line in entries
                if (row := json.loads(line))
                and row["partition"] == "train" and row["locale"] == "vi-VN"
                and row["intent"] in massive["intents"] and row["id"] not in excluded
                and 8 <= len(row["utt"]) <= 110
                and not any(character.isdigit() for character in row["utt"])
            ]

    return pick_external("massive", massive_rows)


def split_families(rows, salt):
    groups = defaultdict(list)
    for row in rows:
        groups[row["family"]].append(row)
    strata = defaultdict(list)
    for family, records in groups.items():
        row = records[0]
        case = row["case"] if row["label"] == REFUSE else "thuong"
        strata[row["label"], case].append(family)
    result = {split: [] for split in SPLITS}
    for stratum, families in sorted(strata.items()):
        families.sort()
        random.Random(f"{SEED}:{salt}:{stratum}").shuffle(families)
        n = len(families)
        held = min(round(0.16 * n), (n - 1) // 2)
        subsets = families[2 * held:], families[:held], families[held:2 * held]
        for split, subset in zip(SPLITS, subsets, strict=True):
            for family in subset:
                result[split].extend(groups[family])
    return result


def datasets(task):
    originals = jsonl(ROOT / "data" / "seeds" / f"{task}.jsonl")
    negatives = jsonl(ROOT / "data" / "negatives.jsonl")
    originals.extend(row for row in negatives if row["task"] == task)
    shared = [row for row in negatives if row["task"] == "*"]
    result = split_families(originals, task)
    for source, rows in {"shared": shared, "massive": external_rows()}.items():
        for split, items in split_families(rows, source).items():
            result[split].extend(items)

    for split, rows in result.items():
        result[split] = [
            {**row, "task": task, **({"title": row.get("title", "")} if task == "support" else {})}
            for row in rows
        ]

    rng = random.Random(f"{SEED}:{task}:noise")
    train = result["train"]
    result["train"] = train + [variant for row in train if "source" not in row
                               for variant in variants(row, rng)]
    return result


# Fit softmax weights and biases with seeded stochastic gradient descent and L2 regularization.
def fit(examples, label_count, dimension, l2):
    weights = [[0.0] * dimension for _ in range(label_count)]
    bias = [0.0] * label_count
    scale = 1.0
    order = list(range(len(examples)))
    rng = random.Random(SEED)
    for epoch in range(EPOCHS):
        rng.shuffle(order)
        rate = LEARNING_RATE / (1 + 0.045 * epoch)
        for index in order:
            vector, target = examples[index]
            logits = [
                intercept + scale * sum(weight[bucket] * value for bucket, value in vector.items())
                for intercept, weight in zip(bias, weights, strict=True)
            ]
            scores = probabilities(logits)
            scale *= 1 - rate * l2
            for label_index, (weight, probability) in enumerate(zip(weights, scores, strict=True)):
                step = rate * ((label_index == target) - probability)
                bias[label_index] += step
                adjusted = step / scale
                for bucket, value in vector.items():
                    weight[bucket] += adjusted * value
    return [[round(value * scale, 6) or 0 for value in weight] for weight in weights], [
        round(value, 6) or 0 for value in bias
    ]


# Choose the threshold with the highest coverage that meets accuracy and refusal targets.
def calibrate(model, rows):
    candidates = []
    cases = Counter(row["case"] for row in rows if row["label"] == REFUSE)
    in_scope = sum(row["label"] != REFUSE for row in rows)
    outside = sum(cases.values())
    for row in rows:
        label, confidence, order, fields, spans = rank(model, row, with_spans=True)
        if label != REFUSE and evidence(model, order, fields, spans):
            candidates.append((confidence, label, row["label"], row["case"]))

    candidates.sort(key=lambda item: item[0], reverse=True)
    selected_cases = Counter()
    offered = correct = covered = 0
    best_key = (0, 0, outside)
    best = {
        "threshold": 1.0, "offered": 0, "correct": 0, "covered": 0, "in_scope": in_scope,
        "refused": outside, "outside": outside,
    }
    for threshold, tied in groupby(candidates, key=lambda item: item[0]):
        for _, label, gold, case in tied:
            offered += 1
            correct += label == gold
            covered += gold != REFUSE
            if gold == REFUSE:
                selected_cases[case] += 1
        if correct * 100 < TARGET_PERCENT * offered or any(
            (cases[case] - selected_cases[case]) * 100 < TARGET_PERCENT * cases[case]
            for case in REFUSAL_CASES
        ):
            continue
        refused = outside - selected_cases.total()
        key = (covered, correct / offered, refused)
        if key > best_key:
            best_key = key
            best.update(threshold=threshold, offered=offered, correct=correct,
                        covered=covered, refused=refused)
    return best_key, best


def write_model(model):
    directory = ROOT / "model"
    directory.mkdir(exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=directory, delete=False) as output:
        temp = Path(output.name)
    try:
        temp.write_text(json.dumps(model, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
        os.replace(temp, directory / f"{model['task']}.json")
    finally:
        temp.unlink(missing_ok=True)


def digest_rows(rows):
    return hashlib.sha256("".join(encode(row) + "\n" for row in rows).encode()).hexdigest()


def calibration_data(task, definitions, data, tune_dir):
    if tune_dir is None:
        rows = data["calibration"]
        return rows, digest_rows(rows)
    path = tune_dir / f"{task}.csv"
    content = path.read_bytes()
    return read_input(path, task, definitions, content), hashlib.sha256(content).hexdigest()


# Train each configuration, select the best on the tuning set, and write its weights.
def train(task, definitions, tune_dir=None):
    data = datasets(task)
    rows = data["train"]
    task_definitions = definitions["tasks"][task]
    tune, tune_hash = calibration_data(task, task_definitions, data, tune_dir)
    labels = [*task_definitions["labels"], REFUSE]
    command = command_pattern(tuple(labels))
    indexes = {label: i for i, label in enumerate(labels)}
    gold_counts = Counter(row["label"] for row in rows if "noise" not in row)
    metadata = {
        "format_version": FORMAT_VERSION, "task": task, "labels": labels,
        "label_revision": definitions["revision"], "training_sha256": digest_rows(rows),
        "tuning_sha256": tune_hash, "tuning_source": "csv" if tune_dir is not None else "internal",
        "seed": SEED,
    }
    best = None
    best_key = None
    for feature_name, settings in FEATURE_GRID.items():
        examples = [(extract(input_fields(row), settings, command)[0], indexes[row["label"]]) for row in rows]
        for l2 in L2_GRID:
            weights, bias = fit(examples, len(labels), settings["dimension"], l2)
            model = {
                **metadata, "features": settings, "weights": weights, "bias": bias,
                "training": {"epochs": EPOCHS, "learning_rate": LEARNING_RATE, "l2": l2,
                             "feature_config": feature_name,
                             "original_label_counts": {label: gold_counts[label] for label in labels}},
            }
            key, result = calibrate(model, tune)
            print(json.dumps({"task": task, "features": feature_name, "l2": l2, **result}), flush=True)
            if best_key is None or key > best_key:
                best_key = key
                best = {**model, "threshold": result["threshold"]}

    write_model(best)
    print(f"{task}: " + ", ".join(f"{name}={len(data[name])}" for name in SPLITS)
          + f", tune={len(tune)}, threshold={best['threshold']:.6f}", flush=True)


def main():
    definitions = label_definitions()
    parser = argparse.ArgumentParser()
    parser.add_argument("--task", choices=definitions["tasks"])
    parser.add_argument("--tune", type=Path, help="Directory containing one tuning CSV per task")
    args = parser.parse_args()
    for task in (args.task,) if args.task else definitions["tasks"]:
        train(task, definitions, args.tune)


if __name__ == "__main__":
    main()
