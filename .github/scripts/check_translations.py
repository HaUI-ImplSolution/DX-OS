#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""Check that the English documents follow the Vietnamese ones, and the other way round.

    check_translations.py                  check every pair
    check_translations.py --stamp FILE...  record that the pair of each FILE (either language) is in step
    check_translations.py --stamp --all    record every pair, for the first run

The pairs are the Vietnamese document and its English version:

- docs/NAME.md and docs/en/NAME_EN.md, and ARCHITECTURE.md and docs/en/ARCHITECTURE_EN.md; the site publishes the English ones;
- README.md and README_EN.md;
- website/docs/PATH and website/i18n/en/docusaurus-plugin-content-docs/current/PATH, the pages that are written for the site.

docs/translations.json records, for each pair, the hash of each text at the last time someone made sure the two said
the same thing. A hash that no longer matches means one language was edited and the other may not have been:
update it, then stamp the pair. The check also fails when an English document has no Vietnamese original, when a pair
is not recorded, and when the two texts no longer have the same headings and code blocks.
It checks that the two are in step, not that the translation is right.

Exit code 0 when every pair holds, 1 otherwise.
"""
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "docs" / "translations.json"
SITE = Path("website/i18n/en/docusaurus-plugin-content-docs/current")
# Pages whose English version is built differently from the original on purpose: the English gallery has no section for
# the English interface. They are still checked for changes.
OTHER_STRUCTURE = {"website/docs/hinh-anh.mdx"}


def vietnamese_of(english: Path) -> str:
    """The Vietnamese original of an English document of docs/en, which sits in docs/ or at the root."""
    name = re.sub(r"_EN\.md$", ".md", english.name)
    return ("docs/" + name) if (ROOT / "docs" / name).exists() else name


def pairs() -> dict[str, str]:
    """Vietnamese path -> English path, for every English document of the repository."""
    found = {"README.md": "README_EN.md"}
    for path in sorted((ROOT / "docs" / "en").glob("*_EN.md")):
        found[vietnamese_of(path)] = path.relative_to(ROOT).as_posix()
    for path in sorted((ROOT / SITE).rglob("*")):
        relative = path.relative_to(ROOT / SITE)
        # tai-lieu is generated from docs/en by website/scripts/sync-docs.mjs and is not committed
        if path.is_file() and path.suffix in (".md", ".mdx") and relative.parts[0] != "tai-lieu":
            found[(Path("website/docs") / relative).as_posix()] = (SITE / relative).as_posix()
    return found


def body(text: str) -> str:
    text = text.replace("\r\n", "\n")
    match = re.match(r"^---\n.*?\n---\n", text, re.S)
    return (text[match.end():] if match else text).strip()


def digest(path: str) -> str:
    return hashlib.sha256(body((ROOT / path).read_text(encoding="utf-8")).encode()).hexdigest()[:16]


def outline(text: str) -> tuple[list[int], list[str]]:
    """The level of each heading and the language of each fenced code block."""
    levels, fences, fence = [], [], None
    for line in body(text).split("\n"):
        mark = re.match(r"^\s*(`{3,}|~{3,})\s*([\w-]*)", line)
        if mark:
            if fence is None:
                fence = mark.group(1)[0]
                fences.append(mark.group(2))
            elif mark.group(1)[0] == fence:
                fence = None
            continue
        heading = re.match(r"^(#{1,6})\s+\S", line)
        if fence is None and heading:
            levels.append(len(heading.group(1)))
    return levels, fences


def main(argv: list[str]) -> int:
    found = pairs()
    recorded = json.loads(MANIFEST.read_text(encoding="utf-8")) if MANIFEST.exists() else {}

    if "--stamp" in argv:
        wanted = [a for a in argv if not a.startswith("--")]
        chosen = list(found) if "--all" in argv else []
        for name in wanted:
            name = Path(name).resolve().relative_to(ROOT).as_posix()
            vietnamese = next((v for v, e in found.items() if name in (v, e)), None)
            if vietnamese is None:
                print(f"check_translations: {name} is not part of a pair")
                return 1
            chosen.append(vietnamese)
        if not chosen:
            print("check_translations: name the files to stamp, or use --all")
            return 1
        for vietnamese in chosen:
            recorded[vietnamese] = {"vi": digest(vietnamese), "en": digest(found[vietnamese])}
        recorded = {k: recorded[k] for k in sorted(recorded) if k in found}
        MANIFEST.write_text(json.dumps(recorded, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"check_translations: {len(chosen)} pair(s) stamped in {MANIFEST.relative_to(ROOT)}")
        return 0

    problems = []
    for english in sorted((ROOT / "docs" / "en").glob("*_EN.md")):
        original = vietnamese_of(english)
        if not (ROOT / original).exists():
            problems.append(f"{english.relative_to(ROOT)}: there is no Vietnamese original {original}")
    for vietnamese, english in found.items():
        for path in (vietnamese, english):
            if not (ROOT / path).exists():
                problems.append(f"{path}: missing, but it is half of the pair {vietnamese} / {english}")
        if not (ROOT / vietnamese).exists() or not (ROOT / english).exists():
            continue
        state = recorded.get(vietnamese)
        if state is None:
            problems.append(f"{english}: the pair is not recorded; check that it says what {vietnamese} says, then stamp it")
        else:
            changed = [name for name, path, key in (("Vietnamese", vietnamese, "vi"), ("English", english, "en")) if digest(path) != state[key]]
            if changed:
                other = "the English" if changed == ["Vietnamese"] else "the Vietnamese" if changed == ["English"] else "both"
                problems.append(
                    f"{vietnamese} / {english}: {' and '.join(changed)} changed since the pair was last stamped; "
                    f"make sure {other} says the same, then run check_translations.py --stamp {vietnamese}"
                )
        if vietnamese not in OTHER_STRUCTURE:
            levels_vi, fences_vi = outline((ROOT / vietnamese).read_text(encoding="utf-8"))
            levels_en, fences_en = outline((ROOT / english).read_text(encoding="utf-8"))
            if levels_vi != levels_en:
                problems.append(f"{english}: the headings differ from {vietnamese} ({len(levels_en)} against {len(levels_vi)}, or a different level)")
            if fences_vi != fences_en:
                problems.append(f"{english}: the code blocks differ from {vietnamese} in number or language")
    for vietnamese in recorded:
        if vietnamese not in found:
            problems.append(f"docs/translations.json: {vietnamese} is recorded but is no longer part of a pair")

    for problem in problems:
        print(problem)
    print(f"check_translations: {len(found)} pair(s), {len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
