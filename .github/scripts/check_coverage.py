# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""The test coverage of the backend, as a Markdown table.

    cd backend && ./mvnw verify && cd .. && python3 .github/scripts/check_coverage.py backend

It adds up the JaCoCo report of each module (target/site/jacoco/jacoco.csv) and fails when there is none.
"""
import csv
import sys
from pathlib import Path

COUNTERS = ("LINE", "BRANCH")


def totals(report: Path) -> dict[str, tuple[int, int]]:
    """counter -> (covered, all) of one jacoco.csv."""
    with report.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    return {
        counter: (sum(int(row[f"{counter}_COVERED"]) for row in rows),
                  sum(int(row[f"{counter}_COVERED"]) + int(row[f"{counter}_MISSED"]) for row in rows))
        for counter in COUNTERS
    }


def share(covered: int, every: int) -> str:
    return f"{100 * covered / every:.1f}% ({covered}/{every})" if every else "-"


def table(modules: dict[str, dict[str, tuple[int, int]]]) -> str:
    lines = ["| Module | Lines | Branches |", "|---|---|---|"]
    whole = {counter: tuple(sum(module[counter][i] for module in modules.values()) for i in (0, 1)) for counter in COUNTERS}
    for name, module in [*modules.items(), ("backend", whole)]:
        lines.append(f"| {name} | {share(*module['LINE'])} | {share(*module['BRANCH'])} |")
    return "\n".join(lines)


def main(argv: list[str]) -> int:
    if len(argv) != 1:
        print(__doc__)
        return 2
    reports = sorted(Path(argv[0]).glob("*/target/site/jacoco/jacoco.csv"))
    if not reports:
        print(f"check_coverage: no JaCoCo report under {argv[0]}; run ./mvnw verify first")
        return 1
    print(table({report.parents[3].name: totals(report) for report in reports}))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
