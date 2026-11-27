#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""Check the names of the jobs of the workflows.

    check_workflow_names.py

A job name is a verb and its object, at most four words, such as `Test the API`; a job of a matrix adds the variant in
parentheses at the end, such as `Test the API (model and process)`. The verb says what kind of work the job does:

    Build, Test      build the product, or run it to check what it does
    Check, Validate  check the repository with a script, or check that a configuration is valid
    Lint, Scan       analyze the code or the configuration, or scan it for security problems
    Plan, Package, Publish, Rerun, Summarize   the jobs that release or follow up

Job names are the contexts of the required checks in .github/rulesets/main.json, which holds the name of a job and
not of its workflow, so two workflows must not use the same name. Every job also names itself, and every name that
the ruleset requires must belong to a job.

Exit code 0 when every name holds, 1 otherwise.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VERBS = {"Build", "Test", "Check", "Validate", "Lint", "Scan", "Plan", "Package", "Publish", "Rerun", "Summarize"}
MAX_WORDS = 4


def jobs_of(text: str) -> dict[str, str | None]:
    """Job id -> its name, read from the lines of a workflow, so that no YAML library is needed on the runner."""
    jobs: dict[str, str | None] = {}
    inside, current = False, None
    for line in text.splitlines():
        if re.match(r"^\S", line):
            inside = line.startswith("jobs:")
            continue
        job = re.match(r"^  ([\w-]+):\s*(#.*)?$", line)
        if inside and job:
            current = job.group(1)
            jobs[current] = None
        name = re.match(r"^    name:\s*(.+?)\s*$", line)
        if inside and current and name:
            jobs[current] = name.group(1).strip("'\"")
    return jobs


def main() -> int:
    problems = []
    names: dict[str, str] = {}
    for path in sorted((ROOT / ".github" / "workflows").glob("*.yml")):
        for job, name in jobs_of(path.read_text(encoding="utf-8")).items():
            where = f"{path.name}: job {job}"
            if not name:
                problems.append(f"{where}: has no name")
                continue
            # the variant of a matrix job is written after the name, in parentheses
            base = re.sub(r"\s*\(.*\)$", "", name)
            if base.split()[0] not in VERBS:
                problems.append(f"{where}: the name {name!r} does not start with one of {', '.join(sorted(VERBS))}")
            if len(base.split()) > MAX_WORDS:
                problems.append(f"{where}: the name {name!r} has more than {MAX_WORDS} words")
            if base in names:
                problems.append(f"{where}: the name {base!r} is also used in {names[base]}")
            names[base] = path.name

    ruleset = json.loads((ROOT / ".github" / "rulesets" / "main.json").read_text(encoding="utf-8"))
    for rule in ruleset["rules"]:
        for check in rule.get("parameters", {}).get("required_status_checks", []):
            context = check["context"]
            if re.sub(r"\s*\(.*\)$", "", context) not in names:
                problems.append(f".github/rulesets/main.json: the required check {context!r} is not the name of a job")

    for problem in problems:
        print(problem)
    print(f"check_workflow_names: {len(names)} job name(s), {len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
