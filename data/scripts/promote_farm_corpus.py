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
BLOCKING_REVIEW_FLAGS = frozenset({"CONTAINS_TREATMENT_REFERENCES"})


def has_blocking_review_flag(item: dict[str, object]) -> bool:
    approved = item.get("review_status") == "APPROVED" and item.get("approved_by")
    return not approved and bool(BLOCKING_REVIEW_FLAGS.intersection(item.get("review_flags", [])))


def promotable_documents(documents: list[dict[str, object]]) -> list[dict[str, object]]:
    """Honor recorded approval; hold flagged documents without an approval record."""
    return [item for item in documents if not has_blocking_review_flag(item)]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--approve-all",
        action="store_true",
        help="Record explicit operator approval for each eligible staged document.",
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

    approved_documents = promotable_documents(documents)
    held_documents = [item for item in documents if item not in approved_documents]
    for item in held_documents:
        item["review_status"] = "IN_REVIEW"
        item["document_type"] = "farm_knowledge_staging"
        for field in ("approved_by", "approved_at", "approval_note"):
            item.pop(field, None)
    # Keep the complete approved source catalogue available to the backend;
    # only treatment-bearing documents are withheld from the ingest corpus.
    approved_registry = registry
    approved_document_ids = {
        item.get("document_id") for item in approved_documents
    }

    for item in approved_documents:
        # Run provenance computes fingerprints outside Git.
        item.get("provenance", {}).pop("sha256", None)
        item["review_status"] = "APPROVED"
        item["approved_by"] = args.reviewer.strip()
        item["approved_at"] = datetime.now(timezone.utc).date().isoformat()
        item["approval_note"] = (
            "Operator-approved for grounded Farm knowledge RAG. "
            "Treatment facts remain subject to Backend safety rules."
        )
        item["document_type"] = "farm_knowledge"

    approved_source_ids = {
        source_id
        for item in approved_documents
        for source_id in item.get("provenance", {}).get("source_ids", [])
    }
    for item in registry:
        source_ids = {item.get("source_id")}
        if source_ids <= approved_source_ids:
            item["review_status"] = "APPROVED"
            item["approved_by"] = args.reviewer.strip()
            item["approved_at"] = datetime.now(timezone.utc).date().isoformat()
        else:
            item["review_status"] = "IN_REVIEW"
            for field in ("approved_by", "approved_at", "approval_note"):
                item.pop(field, None)

    (CANONICAL / "processed" / "farm_knowledge.json").write_text(
        json.dumps(approved_documents, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (CANONICAL / "farm_knowledge_source_registry.json").write_text(
        json.dumps(approved_registry, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (STAGING / "processed" / "farm_knowledge_staging.json").write_text(
        json.dumps(documents, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (STAGING / "source_registry.json").write_text(
        json.dumps(registry, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    queue = json.loads((STAGING / "review_queue.json").read_text(encoding="utf-8"))
    for item in queue:
        if item.get("document_id") in approved_document_ids:
            item["review_status"] = "APPROVED"
            item["approved_by"] = args.reviewer.strip()
            item["approved_at"] = datetime.now(timezone.utc).date().isoformat()
        elif item.get("document_id") in {
            doc.get("document_id") for doc in held_documents
        }:
            item["review_status"] = "IN_REVIEW"
            for field in ("approved_by", "approved_at", "approval_note"):
                item.pop(field, None)
    (STAGING / "review_queue.json").write_text(
        json.dumps(queue, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    held_ids = ", ".join(item.get("document_id", "<unknown>") for item in held_documents)
    print(f"Promoted approved Farm documents: {len(approved_documents)}")
    print(f"Held for treatment review: {len(held_documents)} ({held_ids or 'none'})")
    print(f"Canonical corpus: {CANONICAL / 'processed' / 'farm_knowledge.json'}")


if __name__ == "__main__":
    main()
