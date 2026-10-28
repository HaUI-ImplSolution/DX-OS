# SPDX-License-Identifier: Apache-2.0
"""Offline validator for Phase 2 RAG artifacts.

It validates manifests and corpus contracts without PostgreSQL, embeddings, or
network access.  It is safe to run in a low-disk development environment.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[2]


def read(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def load_quarantined_source_ids(path: Path) -> set[str]:
    manifest = read(path)
    sources = manifest.get("sources", [])
    if not isinstance(sources, list):
        raise ValueError(f"{path}: sources must be an array")
    return {item["source_id"] for item in sources}


def validate_registry(
    path: Path, namespace: str, quarantined_source_ids: set[str] | None = None,
    allowed_hosts: set[str] | None = None,
) -> set[str]:
    quarantined_source_ids = quarantined_source_ids or set()
    allowed_hosts = allowed_hosts or set(read(ROOT / "data/rag/chat_profiles.json")[namespace]["allowed_hosts"])
    records = read(path)
    if not isinstance(records, list) or not records:
        raise ValueError(f"{path}: registry must be a non-empty array")
    seen_ids: set[str] = set()
    active_ids: set[str] = set()
    for item in records:
        required = {"source_id", "url", "organization", "authority_level", "accessed_at"}
        missing = required - item.keys()
        if missing:
            raise ValueError(f"{path}: {item.get('source_id', '<unknown>')} missing {sorted(missing)}")
        source_id = item["source_id"]
        if source_id in seen_ids:
            raise ValueError(f"{path}: duplicate source_id {source_id}")
        seen_ids.add(source_id)
        parsed = urlparse(item["url"])
        if parsed.scheme != "https" or not parsed.netloc:
            raise ValueError(f"{path}: non-HTTPS URL for {source_id}")
        if source_id in quarantined_source_ids:
            continue
        active_ids.add(source_id)
        if parsed.hostname not in allowed_hosts:
            raise ValueError(f"{path}: source outside approved hosts: {item['url']}")
        if namespace == "farm" and item.get("language", "vi") not in {"vi", "vi-VN"}:
            raise ValueError(f"{path}: Farm source is not Vietnamese: {source_id}")
    return active_ids


def validate_corpus(
    path: Path,
    source_ids: set[str],
    label: str,
    quarantined_source_ids: set[str] | None = None,
    required_review_status: str | None = None,
) -> int:
    quarantined_source_ids = quarantined_source_ids or set()
    documents = read(path)
    if not isinstance(documents, list) or not documents:
        raise ValueError(f"{path}: corpus must be a non-empty array")
    seen: set[str] = set()
    active_count = 0
    for item in documents:
        key = item.get("document_id") or item.get("article_code") or item.get("disease_code")
        if not key or key in seen:
            raise ValueError(f"{path}: missing or duplicate document identifier {key!r}")
        seen.add(key)
        if not item.get("content", "").strip():
            raise ValueError(f"{path}: empty content in {key}")
        if label == "farm_knowledge":
            if item.get("language") not in {"vi", "vi-VN"}:
                raise ValueError(f"{path}: Farm document is not Vietnamese {key}")
        if required_review_status and item.get("review_status") != required_review_status:
            raise ValueError(
                f"{path}: {key} has review_status {item.get('review_status')!r}; "
                f"expected {required_review_status!r}"
            )
        referenced = item.get("provenance", {}).get("source_ids", [])
        if set(referenced) & quarantined_source_ids:
            continue
        active_count += 1
        unknown = set(referenced) - source_ids
        if unknown:
            raise ValueError(f"{path}: {key} references unknown sources {sorted(unknown)}")
    return active_count


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args()
    root = args.root.resolve()
    farm_sources = validate_registry(root / "data/rag/staging/farm/source_registry.json", "farm")
    farm_count = validate_corpus(
        root / "data/rag/staging/farm/processed/farm_knowledge_staging.json",
        farm_sources,
        "farm_knowledge",
        required_review_status="APPROVED",
    )
    quarantined = load_quarantined_source_ids(root / "data/rag/lab/quarantine_manifest.json")
    lab_sources = validate_registry(
        root / "data/rag/lab/source_registry.json", "lab", quarantined
    )
    lab_count = validate_corpus(
        root / "data/rag/lab/processed/lab_knowledge.json",
        lab_sources,
        "lab",
        quarantined,
    )
    print(
        "Phase 2 RAG artifact validation: PASS "
        f"(farm_staging={farm_count}, lab_active={lab_count}, "
        f"lab_quarantined={len(quarantined)})"
    )


if __name__ == "__main__":
    main()
