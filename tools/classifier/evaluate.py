# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0

import argparse
from collections import Counter
import hashlib
import json
from math import ceil
import os
from pathlib import Path
from time import perf_counter_ns
import tempfile

from features import FORMAT_VERSION, REFUSE, normalized, predict
from fetch import ROOT
from train import datasets, label_definitions, read_input

HEADER = """<!-- SPDX-FileCopyrightText: 2026 HaUI-ImplSolution -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Kết quả đo bộ phân loại tiếng Việt

"""


def metrics(expected, proposals):
    offered = sum(label != REFUSE for label in proposals)
    in_scope = sum(label != REFUSE for label in expected)
    covered = sum(gold != REFUSE and pred != REFUSE
                  for gold, pred in zip(expected, proposals, strict=True))
    correct = sum(gold == pred and pred != REFUSE
                  for gold, pred in zip(expected, proposals, strict=True))
    refused_correctly = sum(gold == pred == REFUSE
                            for gold, pred in zip(expected, proposals, strict=True))
    return offered, correct, covered, in_scope, refused_correctly, len(expected) - in_scope


def percent(numerator, denominator):
    return f"{100 * numerator / denominator:.1f}% ({numerator}/{denominator})" if denominator else "— (0/0)"


def measure_latency(model, rows):
    for i in range(25):
        predict(model, rows[i % len(rows)])

    def timings(batch, samples):
        elapsed = []
        for start in range(samples):
            tick = perf_counter_ns()
            for offset in range(batch):
                predict(model, rows[(start * batch + offset) % len(rows)])
            elapsed.append((perf_counter_ns() - tick) / 1_000_000)
        elapsed.sort()
        return [elapsed[ceil(p * samples) - 1] for p in (0.5, 0.95)]

    return timings(1, 150), timings(25, 30)


def baselines(model, rows, covered):
    counts = model["training"]["original_label_counts"]
    choices = model["labels"][:-1]
    popular = max(choices, key=counts.__getitem__)
    keys = {label: " " + normalized(label) + " " for label in choices}
    names = []
    for row in rows:
        fields = [" " + normalized(row.get(field, "")) + " " for field in ("title", "text")]
        hits = {label: sum(value.count(key) for value in fields) for label, key in keys.items()}
        best = max(choices, key=lambda label: (hits[label], counts[label]))
        names.append((hits[best], best))

    tie_order = sorted(
        range(len(rows)),
        key=lambda i: hashlib.sha256(f"{model['seed']}:{rows[i]['id']}".encode()).digest(),
    )

    def match_coverage(ranking, labels):
        proposals = [REFUSE] * len(rows)
        remaining = covered
        for i in ranking:
            if not remaining:
                break
            proposals[i] = labels[i]
            remaining -= rows[i]["label"] != REFUSE
        return proposals

    return {
        "Từ khóa theo tên nhãn": match_coverage(
            sorted(tie_order, key=lambda i: names[i][0], reverse=True), [label for _, label in names]
        ),
        "Luôn chọn nhãn phổ biến": match_coverage(tie_order, [popular] * len(rows)),
    }


def table(headers, rows):
    return [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
        *("| " + " | ".join(str(value).replace("|", r"\|").replace("\n", " ") for value in row) + " |"
          for row in rows),
    ]


def report_task(model, rows, model_bytes):
    labels = model["labels"]
    expected = [row["label"] for row in rows]
    predicted = [predict(model, row)["label"] for row in rows]
    offered, correct, covered, scope, rejected, out_scope = metrics(expected, predicted)
    cases = Counter(row["case"] for row in rows if row["label"] == REFUSE)
    case_rejected = Counter(
        row["case"] for row, pred in zip(rows, predicted, strict=True) if row["label"] == pred == REFUSE
    )
    matrix = Counter(zip(expected, predicted, strict=True))

    lines = [
        f"## {model['task']}", "",
        f"Tập đo có {len(rows)} câu, trong đó {scope} câu đúng phạm vi và {out_scope} câu cần từ chối.",
        f"Ngưỡng là `{model['threshold']:.6f}` và tệp trọng số có {model_bytes} byte.",
        "",
        *table(["Chỉ số", "Kết quả"], [
            ("Đúng trên câu có đề xuất", percent(correct, offered)),
            ("Độ phủ trên câu đúng phạm vi", percent(covered, scope)),
            ("Từ chối đúng", percent(rejected, out_scope)),
        ]),
        "", "### Từ chối theo loại ca", "",
        *table(["Loại ca", "Từ chối đúng"], [
            (case, percent(case_rejected[case], count)) for case, count in sorted(cases.items())
        ]),
        "", "### Ma trận nhầm lẫn", "",
        *table(["Nhãn đúng / nhãn dự đoán", *labels], [
            [gold, *(matrix[gold, pred] for pred in labels)] for gold in labels
        ]),
        "", "### Kết quả theo văn phong", "",
    ]

    styles = []
    for style in sorted({row["style"] for row in rows}):
        subset = [(row["label"], pred) for row, pred in zip(rows, predicted, strict=True)
                  if row["style"] == style]
        total, right, cov, inside, reject, outside = metrics(*zip(*subset, strict=True))
        styles.append((style, len(subset), percent(right, total), percent(cov, inside), percent(reject, outside)))
    lines += table(["Văn phong", "Số câu", "Đúng trên đề xuất", "Độ phủ", "Từ chối đúng"], styles)

    comparison = [("Softmax", percent(correct, offered), percent(covered, scope), percent(rejected, out_scope))]
    for name, predictions in baselines(model, rows, covered).items():
        total, right, cov, inside, reject, outside = metrics(expected, predictions)
        comparison.append((name, percent(right, total), percent(cov, inside), percent(reject, outside)))
    lines += ["", "### So sánh ở cùng độ phủ", "",
              *table(["Cách đo", "Đúng trên đề xuất", "Độ phủ", "Từ chối đúng"], comparison)]

    single, batch = measure_latency(model, rows)
    lines += ["", "### Độ trễ suy luận (ms)", "",
              *table(["Lượt đo", "p50", "p95"], [
                  ("Một câu", f"{single[0]:.3f}", f"{single[1]:.3f}"),
                  ("Lô 25 câu", f"{batch[0]:.3f}", f"{batch[1]:.3f}"),
              ]), ""]
    return "\n".join(lines)


def write_report(path, task, section, is_dev, tasks):
    if is_dev:
        parts = {}
        if path.is_file():
            for block in path.read_text(encoding="utf-8").split("\n## ")[1:]:
                name = block.split("\n", 1)[0]
                if name in tasks:
                    parts[name] = "## " + block.rstrip() + "\n"
        parts[task] = section
        description = (
            "Tập dev giữ riêng theo họ câu gồm câu nháp giả lập và câu lạc đề lấy từ nguồn ngoài.\n"
            "Các số liệu này là kết quả phát triển, chưa phải kết quả nghiệm thu.\n"
            "Độ trễ đo ở tiến trình Python đã làm nóng, gồm sinh cụm căn cứ cho đề xuất.\n\n"
        )
        body = description + "\n".join(parts[name] for name in tasks if name in parts)
    else:
        body = section
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as output:
        temp = Path(output.name)
    try:
        temp.write_text(HEADER + body, encoding="utf-8")
        os.replace(temp, path)
    finally:
        temp.unlink(missing_ok=True)


def main():
    definitions = label_definitions()
    tasks = definitions["tasks"]
    parser = argparse.ArgumentParser()
    parser.add_argument("--task", required=True, choices=tasks)
    parser.add_argument("--input", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    model_path = ROOT / "model" / f"{args.task}.json"
    model_bytes = model_path.read_bytes()
    model = json.loads(model_bytes)
    if (model["format_version"] != FORMAT_VERSION or model["task"] != args.task
            or model["label_revision"] != definitions["revision"]
            or model["labels"] != [*tasks[args.task]["labels"], REFUSE]):
        raise ValueError(f"Incompatible model: {model_path}")

    rows = read_input(args.input, args.task, tasks[args.task]) if args.input else datasets(args.task)["dev"]
    section = report_task(model, rows, len(model_bytes))
    print(section)
    if args.output is not None:
        write_report(args.output, args.task, section, args.input is None, tasks)
        print(f"Báo cáo đã ghi vào {args.output}.")


if __name__ == "__main__":
    main()
