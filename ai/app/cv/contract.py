# SPDX-License-Identifier: Apache-2.0
"""Load and validate the single canonical CV class contract."""

import json
from pathlib import Path
from typing import Any


def load_class_contract(path: Path) -> dict[str, Any]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise ValueError(f"CV class contract does not exist: {path}") from exc
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"CV class contract is not readable JSON: {path}") from exc

    classes = payload.get("classes")
    fallback = payload.get("fallback")
    if not isinstance(classes, list) or not classes:
        raise ValueError("CV class contract must contain a non-empty classes list")
    if not isinstance(fallback, dict):
        raise TypeError("CV class contract must contain a fallback object")

    required = {"class_id", "model_label", "disease_code", "disease_name"}
    if any(
        not isinstance(item, dict) or not required <= item.keys() for item in classes
    ):
        raise ValueError("CV class contract contains an incomplete class entry")
    class_ids = [item["class_id"] for item in classes]
    if class_ids != list(range(len(classes))):
        raise ValueError("CV class IDs must be contiguous and ordered from zero")
    if len({item["model_label"] for item in classes}) != len(classes):
        raise ValueError("CV model labels must be unique")
    if len({item["disease_code"] for item in classes}) != len(classes):
        raise ValueError("CV disease codes must be unique")
    if not fallback.get("disease_code") or not fallback.get("disease_name"):
        raise ValueError("CV fallback must define disease_code and disease_name")
    return payload


def class_contract(path: Path | None = None) -> dict[str, Any]:
    if path is None:
        from app.core.config import get_settings

        path = get_settings().cv_class_contract_path
    return load_class_contract(path)


def class_entry(class_id: int, contract: dict[str, Any]) -> dict[str, Any]:
    classes = contract["classes"]
    if not 0 <= class_id < len(classes):
        raise ValueError(f"Unknown model class ID: {class_id}")
    entry = classes[class_id]
    if entry["class_id"] != class_id:
        raise ValueError("CV class contract order is invalid")
    return entry


def canonical_code(model_label: str, contract: dict[str, Any] | None = None) -> str:
    contract = contract or class_contract()
    matches = [
        item["disease_code"]
        for item in contract["classes"]
        if item["model_label"] == model_label
    ]
    if len(matches) != 1:
        raise ValueError(f"Unknown model label: {model_label}")
    return matches[0]
