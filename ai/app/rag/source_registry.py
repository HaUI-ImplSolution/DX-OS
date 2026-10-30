# SPDX-License-Identifier: AGPL-3.0-only
import json
from functools import lru_cache
from pathlib import Path
from typing import Any

REGISTRY_FILE = (
    Path(__file__).resolve().parents[3] / "data" / "rag" / "source_registry.json"
)

REQUIRED_FIELDS = {
    "source_id",
    "title",
    "organization",
    "source_type",
    "recommended_use",
    "url",
    "authority_level",
    "accessed_at",
}


def load_source_registry(path: Path | None = None) -> dict[str, dict[str, Any]]:
    """Load and validate a RAG source registry, indexed by source ID.

    Without a path the registry of the farm corpus is read.
    """

    return _load_registry(path or REGISTRY_FILE)


@lru_cache(maxsize=4)
def _load_registry(path: Path) -> dict[str, dict[str, Any]]:
    with path.open(encoding="utf-8") as file:
        records = json.load(file)

    if not isinstance(records, list):
        raise TypeError("source_registry.json must contain a JSON array")

    registry: dict[str, dict[str, Any]] = {}
    for index, record in enumerate(records, start=1):
        if not isinstance(record, dict):
            raise TypeError(f"Source item {index} must be an object")

        missing = REQUIRED_FIELDS - record.keys()
        if missing:
            missing_text = ", ".join(sorted(missing))
            raise ValueError(f"Source item {index} is missing fields: {missing_text}")

        source_id = record["source_id"]
        if not isinstance(source_id, str) or not source_id:
            raise ValueError(f"Source item {index} has an invalid source_id")
        if source_id in registry:
            raise ValueError(f"Duplicate source_id: {source_id}")

        url = record["url"]
        if not isinstance(url, str) or not url.startswith(("https://", "http://")):
            raise ValueError(f"Source {source_id} has an invalid URL")

        registry[source_id] = record

    return registry


def resolve_sources(source_ids: list[str]) -> list[dict[str, Any]]:
    """Resolve source IDs without changing their order."""

    registry = load_source_registry()
    unknown = [source_id for source_id in source_ids if source_id not in registry]
    if unknown:
        raise ValueError(f"Unknown source IDs: {', '.join(unknown)}")
    return [registry[source_id] for source_id in source_ids]
