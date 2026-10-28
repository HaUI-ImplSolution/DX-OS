"""Promote an explicitly approved Farm staging corpus to the canonical corpus.

The command is intentionally explicit: it refuses to promote unless
``--approve-all`` is supplied. Approval here permits RAG knowledge use; it
does not create pesticide, dose, or PHI treatment rules.
"""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
STAGING = ROOT / "data" / "rag" / "staging" / "farm"
CANONICAL = ROOT / "data" / "rag"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--approve-all",
        action="store_true",
        help="Record explicit operator approval for every staged document.",
    )
    parser.add_argument("--reviewer", help="Identity of the person authorizing promotion.")
    args = parser.parse_args()
    if not args.approve_all:
        raise SystemExit("Refusing promotion: pass --approve-all after human review.")

    if not args.reviewer or not args.reviewer.strip():
        raise SystemExit("Refusing promotion: --reviewer is required after human review.")

    data_path = STAGING / "processed" / "farm_knowledge_staging.json"
    registry_path = STAGING / "source_registry.json"
    documents = json.loads(data_path.read_text(encoding="utf-8"))
    registry = json.loads(registry_path.read_text(encoding="utf-8"))
    if not documents or not registry:
        raise ValueError("Staging corpus and registry must not be empty")

    for item in documents:
        item["review_status"] = "APPROVED"
        item["approved_by"] = args.reviewer.strip()
        item["approved_at"] = datetime.now(timezone.utc).date().isoformat()
        item["approval_note"] = (
            "Operator-approved for grounded Farm knowledge RAG. "
            "Treatment facts remain subject to Backend safety rules."
        )
        item["document_type"] = "farm_knowledge"

    for item in registry:
        item["review_status"] = "APPROVED"
        item["approved_by"] = args.reviewer.strip()
        item["approved_at"] = datetime.now(timezone.utc).date().isoformat()

    (CANONICAL / "processed" / "farm_knowledge.json").write_text(
        json.dumps(documents, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (CANONICAL / "farm_knowledge_source_registry.json").write_text(
        json.dumps(registry, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (STAGING / "processed" / "farm_knowledge_staging.json").write_text(
        json.dumps(documents, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (STAGING / "source_registry.json").write_text(
        json.dumps(registry, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    queue = json.loads((STAGING / "review_queue.json").read_text(encoding="utf-8"))
    for item in queue:
        item["review_status"] = "APPROVED"
        item["approved_by"] = args.reviewer.strip()
        item["approved_at"] = datetime.now(timezone.utc).date().isoformat()
    (STAGING / "review_queue.json").write_text(
        json.dumps(queue, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"Promoted approved Farm documents: {len(documents)}")
    print(f"Canonical corpus: {CANONICAL / 'processed' / 'farm_knowledge.json'}")


if __name__ == "__main__":
    main()
