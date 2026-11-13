# SPDX-License-Identifier: Apache-2.0
"""Compare the basic ticket handlers in Lab with the Core application YAML."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

BASELINE = "90d62e5f6ace55e6c3ae8bdc9d27a674cf55f657"
CORE = "bab0367f869c0c6fb13b413c4c3048375405fc79"
LAB = "backend/app-lab/src/main/java/HaUI/ImplSolution/dxos/lab/"
SCOPES = {
    "controller/TicketController.java": (
        "create", "list", "detail", "update", "assign", "start", "resolve", "confirm",
        "addAttachment", "removeAttachment",
    ),
    "service/TicketService.java": (
        "create", "list", "detail", "update", "accept", "assign", "start", "resolve", "confirm",
        "addAttachment", "removeAttachment", "detailOf", "timeline", "findVisible",
        "findEditable", "findForUpdateAction", "requireStatus", "requireNotFinished",
        "requireAssigneeOrAdmin", "requireRequester", "store", "toResponse(TicketAttachment",
        "requesterIs", "isBlank",
    ),
}


def git(repo, *args):
    result = subprocess.run(["git", "-C", str(repo), *args], capture_output=True)
    if result.returncode:
        raise ValueError(result.stderr.decode(errors="replace").strip())
    return result.stdout


def revision(repo, ref):
    return git(repo, "rev-parse", "--verify", "--end-of-options", ref + "^{commit}").decode().strip()


def metrics(lines):
    return {"physical_lines": len(lines), "nonblank_lines": sum(bool(line.strip()) for line in lines)}


def handlers(repo, commit, path, names, details):
    lines = git(repo, "show", f"{commit}:{path}").decode("utf-8").splitlines()
    rows, selected = [], set()
    for name in names:
        needle = name if "(" in name else name + "("
        matches = [i for i, line in enumerate(lines)
                   if re.match(r"^  (public|private|static) ", line) and needle in line]
        if len(matches) != 1:
            raise ValueError(f"expected one handler {path}:{name}, found {len(matches)}")
        start = matches[0]
        # The pinned Lab classes indent methods by two spaces.
        end = next((i + 1 for i in range(start + 1, len(lines)) if lines[i] == "  }"), None)
        if end is None:
            raise ValueError(f"missing handler end: {path}:{name}")
        selected.update(range(start, end))
        rows.append({"handler": name + ")" if "(" in name else name,
                     "start_line": start + 1, "end_line": end,
                     **metrics(lines[start:end])})
    result = {"path": path, "handlers": len(rows), **metrics([lines[i] for i in sorted(selected)])}
    if details:
        result["inventory"] = rows
    return result


def measure(repo, baseline, core, model, details):
    old, new = revision(repo, baseline), revision(repo, core)
    groups = [handlers(repo, old, LAB + path, names, details) for path, names in SCOPES.items()]
    total = sum(group["physical_lines"] for group in groups)
    data = model.read_bytes()
    yaml = metrics(data.decode("utf-8").splitlines())
    return {
        "method": "Whole selected Java handlers, signature through closing brace; physical lines include blanks/comments; YAML counted in full",
        "baseline": {"commit": old, "groups": groups, "physical_lines": total,
                     "nonblank_lines": sum(group["nonblank_lines"] for group in groups)},
        "core": {"commit": new, "application_model": {"path": "examples/effort-benchmark/support.yaml",
                 "sha256": hashlib.sha256(data).hexdigest(), **yaml}},
        "comparison": {"removed_physical_lines": total - yaml["physical_lines"],
                       "reduction_percent": round((1 - yaml["physical_lines"] / total) * 100, 2),
                       "meaning": "Reduction in selected handler/YAML lines, not a percentage of working hours"},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--baseline", default=BASELINE)
    parser.add_argument("--core", default=CORE)
    parser.add_argument("--details", action="store_true", help="include every handler and line range")
    args = parser.parse_args()
    try:
        result = measure(args.repo, args.baseline, args.core,
                         Path(__file__).with_name("support.yaml"), args.details)
    except (OSError, UnicodeError, ValueError) as error:
        parser.exit(1, f"Measurement refused: {error}\n")
    json.dump(result, sys.stdout, ensure_ascii=False, indent=2)
    print()


if __name__ == "__main__":
    main()
