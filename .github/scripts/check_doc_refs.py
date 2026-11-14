#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check the references between the documents and the checks they name.

    check_doc_refs.py

Three kinds of reference break without anyone noticing, because the site still builds:

- A citation of docs/TESTING.md, written `docs/TESTING.md` mục 6, «Title» (several titles may follow).
  The section number must be a section of that file, and every «Title» must be the bold title of a
  numbered item of that section.
- A check script named as `check_<name>.py` must exist somewhere in the repository.
- A check label such as CLI-19 or QL-03 must appear in a script of deploy/dev.
  Only prefixes that the scripts themselves use are checked, so the placeholders of the phase
  templates are left alone.

Exit code 0 when every reference holds, 1 otherwise.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TESTING = ROOT / "docs" / "TESTING.md"
SCRIPTS = ROOT / "deploy" / "dev"

CITE = re.compile(r"TESTING\.md`?,?\s+mục\s+(\d+)((?:,?\s*(?:và\s+|đến\s+)?«[^»]+»)*)")
TITLE = re.compile(r"«([^»]+)»")
ITEM = re.compile(r"(?m)^(\d+)\. \*\*(.+?)\*\*")
SECTION = re.compile(r"(?m)^## (\d+)\.")
LABEL = re.compile(r"\b([A-Z]{2,5})-(\d{2})\b")
SCRIPT_NAME = re.compile(r"\bcheck_[a-z_]+\.py\b")


def tracked(pattern: str) -> list[str]:
    return subprocess.run(["git", "ls-files", pattern], cwd=ROOT, capture_output=True, text=True, check=True).stdout.splitlines()


def tracked_markdown() -> list[Path]:
    return [ROOT / p for p in tracked("*.md") if not p.startswith(("website/docs/templates/", "website/docs/tai-lieu/"))]


def testing_sections(text: str) -> dict[int, str]:
    marks = list(SECTION.finditer(text))
    return {int(m.group(1)): text[m.end(): marks[i + 1].start() if i + 1 < len(marks) else len(text)] for i, m in enumerate(marks)}


def item_titles(section: str) -> list[tuple[int, str]]:
    return [(int(m.group(1)), m.group(2).strip().rstrip(":").strip()) for m in ITEM.finditer(section)]


def main() -> int:
    problems: list[str] = []
    text = TESTING.read_text(encoding="utf-8")
    sections = testing_sections(text)

    for number, section in sections.items():
        items = item_titles(section)
        numbers = [n for n, _ in items]
        if numbers and numbers != list(range(1, len(numbers) + 1)):
            problems.append(f"docs/TESTING.md: the items of section {number} are not numbered 1 to {len(numbers)} in order")
        titles = [t for _, t in items]
        for title in {t for t in titles if titles.count(t) > 1}:
            problems.append(f"docs/TESTING.md: two items of section {number} are titled «{title}»")
    # The manual checks are numbered across their sub-sections, so they form one list.
    manual = [(n, t) for n, t in item_titles(sections.get(6, ""))]
    numbers = [n for n, _ in manual]
    if numbers != list(range(1, len(numbers) + 1)):
        problems.append("docs/TESTING.md: the manual checks of section 6 are not numbered 1 to N in order")
    manual_titles = {t for _, t in manual}

    label_prefixes: set[str] = set()
    labels_in_scripts: set[str] = set()
    for script in SCRIPTS.glob("*.py"):
        for prefix, digits in LABEL.findall(script.read_text(encoding="utf-8")):
            labels_in_scripts.add(f"{prefix}-{digits}")
            label_prefixes.add(prefix)
    script_names = {Path(p).name for p in tracked("*check_*.py")}

    for path in tracked_markdown():
        body = path.read_text(encoding="utf-8")
        name = path.relative_to(ROOT).as_posix()
        for m in CITE.finditer(body):
            section = int(m.group(1))
            if section not in sections:
                problems.append(f"{name}: cites section {section} of docs/TESTING.md, which does not exist")
                continue
            for title in TITLE.findall(m.group(2)):
                if section != 6 or title not in manual_titles:
                    problems.append(f"{name}: cites «{title}» of docs/TESTING.md section {section}, which has no item with that title")
        for script in sorted(set(SCRIPT_NAME.findall(body))):
            if script not in script_names:
                problems.append(f"{name}: names {script}, which is not in the repository")
        if name in ("website/docs/evidence-matrix.md", "docs/TESTING.md", "docs/MODEL.md", "deploy/OPERATIONS.md"):
            for prefix, digits in LABEL.findall(body):
                label = f"{prefix}-{digits}"
                if prefix in label_prefixes and label not in labels_in_scripts:
                    problems.append(f"{name}: names the check {label}, which no script of deploy/dev has")

    for problem in sorted(set(problems)):
        print(problem)
    print(f"check_doc_refs: {len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
