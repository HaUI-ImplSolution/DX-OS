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


def validate_registry(path: Path, namespace: str) -> set[str]:
    records = read(path)
    if not isinstance(records, list) or not records:
        raise ValueError(f"{path}: registry must be a non-empty array")
    ids: set[str] = set()
    for item in records:
        required = {"source_id", "url", "organization", "authority_level", "accessed_at"}
        missing = required - item.keys()
        if missing:
            raise ValueError(f"{path}: {item.get('source_id', '<unknown>')} missing {sorted(missing)}")
        source_id = item["source_id"]
        if source_id in ids:
            raise ValueError(f"{path}: duplicate source_id {source_id}")
        ids.add(source_id)
        parsed = urlparse(item["url"])
        if parsed.scheme != "https" or not parsed.netloc:
            raise ValueError(f"{path}: non-HTTPS URL for {source_id}")
        if namespace == "lab" and parsed.hostname != "sict.haui.edu.vn":
            raise ValueError(f"{path}: Lab source outside sict.haui.edu.vn: {item['url']}")
        if namespace == "farm" and item.get("language", "vi") not in {"vi", "vi-VN"}:
            raise ValueError(f"{path}: Farm source is not Vietnamese: {source_id}")
    return ids


def validate_corpus(path: Path, source_ids: set[str], label: str) -> int:
    documents = read(path)
    if not isinstance(documents, list) or not documents:
        raise ValueError(f"{path}: corpus must be a non-empty array")
    seen: set[str] = set()
    for item in documents:
        key = item.get("document_id") or item.get("article_code") or item.get("disease_code")
        if not key or key in seen:
            raise ValueError(f"{path}: missing or duplicate document identifier {key!r}")
        seen.add(key)
        if not item.get("content", "").strip():
            raise ValueError(f"{path}: empty content in {key}")
        if label == "farm_knowledge":
            if item.get("review_status") != "APPROVED":
                raise ValueError(f"{path}: unapproved Farm document {key}")
            if item.get("language") not in {"vi", "vi-VN"}:
                raise ValueError(f"{path}: Farm document is not Vietnamese {key}")
        referenced = item.get("provenance", {}).get("source_ids", [])
        unknown = set(referenced) - source_ids
        if unknown:
            raise ValueError(f"{path}: {key} references unknown sources {sorted(unknown)}")
    return len(documents)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args()
    root = args.root.resolve()
    farm_sources = validate_registry(root / "data/rag/farm_knowledge_source_registry.json", "farm")
    farm_count = validate_corpus(root / "data/rag/processed/farm_knowledge.json", farm_sources, "farm_knowledge")
    lab_sources = validate_registry(root / "data/rag/lab/source_registry.json", "lab")
    lab_count = validate_corpus(root / "data/rag/lab/processed/lab_knowledge.json", lab_sources, "lab")
    print(f"Phase 2 RAG artifact validation: PASS (farm={farm_count}, lab={lab_count})")


if __name__ == "__main__":
    main()
