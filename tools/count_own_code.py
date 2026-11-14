# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""Counts the code a person writes for a system built on DX-OS, by the public rules of docs/TARGETS.md.

    python3 tools/count_own_code.py <directory> [--json]

The directory holds the model files of one system and anything written beside them. A model file is
a YAML file with a top-level `entities:` and `format:`. Its level follows from what it declares:
  level 1  only the entities, the roles and a process family, nothing else about the process;
  level 2  also states, actions, conditions, escalation, automatic closing or follow-up
           actions, all written in the model file;
  level 3  asks a service outside the core, which has to be written: an action with a check.
Own code is every line of source code (Java, Kotlin, Python, JavaScript, TypeScript, SQL, shell,
Go, Ruby, PHP, C#, HTML, CSS) in the directory, blank lines and comment-only lines left out.
Model files and other configuration (YAML, JSON, .env) are declaration, not code, and are counted
apart. The count is 0 for a level 1 or 2 system when nothing but declaration is written.
"""
import json
import os
import re
import sys

CODE = {".java": "//", ".kt": "//", ".py": "#", ".js": "//", ".ts": "//", ".tsx": "//", ".jsx": "//",
        ".sql": "--", ".sh": "#", ".go": "//", ".rb": "#", ".php": "//", ".cs": "//", ".html": None, ".css": None}
SKIP = {".git", "node_modules", "__pycache__", ".venv", "dist", "build", "target"}
LEVEL2 = re.compile(r"^\s*(states|actions|when|escalate|auto_close|after|require|role)\s*:")
LEVEL3 = re.compile(r"^\s*check\s*:")


def lines_of(path, comment):
    count = 0
    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            text = line.strip()
            if text and not (comment and text.startswith(comment)):
                count += 1
    return count


def model_level(path):
    level = 1
    top = {"entities": False, "format": False}
    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            for key in top:
                if line.startswith(key + ":"):
                    top[key] = True
            if LEVEL3.match(line):
                return 3
            if LEVEL2.match(line):
                level = 2
    return level if all(top.values()) else 0


def scan(root):
    models, code, config = [], [], 0
    for folder, names, files in os.walk(root):
        names[:] = [n for n in names if n not in SKIP]
        for name in sorted(files):
            path = os.path.join(folder, name)
            relative = os.path.relpath(path, root)
            extension = os.path.splitext(name)[1].lower()
            if extension in (".yaml", ".yml"):
                level = model_level(path)
                if level:
                    models.append({"file": relative, "level": level, "lines": lines_of(path, "#")})
                else:
                    config += lines_of(path, "#")
            elif extension in CODE:
                code.append({"file": relative, "lines": lines_of(path, CODE[extension])})
            elif extension in (".json", ".env"):
                config += lines_of(path, None)
    return {"directory": root, "models": models, "own_code": code,
            "declaration_lines": sum(m["lines"] for m in models),
            "own_code_lines": sum(c["lines"] for c in code), "other_configuration_lines": config}


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != 1 or not os.path.isdir(args[0]):
        print(__doc__)
        return 2
    result = scan(args[0])
    if "--json" in sys.argv:
        print(json.dumps(result, indent=1))
        return 0
    print(f"{result['directory']}: declaration {result['declaration_lines']} lines in "
          f"{len(result['models'])} model file(s), own code {result['own_code_lines']} lines")
    for model in result["models"]:
        print(f"  level {model['level']}  {model['lines']:4d} lines  {model['file']}")
    for item in result["own_code"]:
        print(f"  code     {item['lines']:4d} lines  {item['file']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
