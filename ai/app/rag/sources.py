# SPDX-License-Identifier: AGPL-3.0-only
import json
from pathlib import Path
from typing import Any

# Every source states where it came from and under which license it may be reused.
REQUIRED_FIELDS = {"source_id", "title", "url", "license"}


def load_sources(path: Path) -> dict[str, dict[str, Any]]:
    """Load and validate a source registry, indexed by source ID."""

    with path.open(encoding="utf-8") as file:
        records = json.load(file)

    if not isinstance(records, list):
        raise TypeError(f"{path.name} must contain a JSON array")

    registry: dict[str, dict[str, Any]] = {}
    for index, record in enumerate(records, start=1):
        if not isinstance(record, dict):
            raise TypeError(f"Source item {index} must be an object")

        missing = REQUIRED_FIELDS - record.keys()
        if missing:
            raise ValueError(
                f"Source item {index} is missing fields: {', '.join(sorted(missing))}"
            )

        source_id = record["source_id"]
        if not isinstance(source_id, str) or not source_id or "," in source_id:
            raise ValueError(f"Source item {index} has an invalid source_id")
        if source_id in registry:
            raise ValueError(f"Duplicate source_id: {source_id}")

        url = record["url"]
        if not isinstance(url, str) or not url.startswith(("https://", "http://")):
            raise ValueError(f"Source {source_id} has an invalid URL")
        if not isinstance(record["license"], str) or not record["license"].strip():
            raise ValueError(f"Source {source_id} has no license")

        registry[source_id] = record

    return registry
