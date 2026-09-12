# SPDX-License-Identifier: Apache-2.0
"""Explicit adapter: preserve model labels and source Disease codes."""

from src.cv.class_names import CLASS_NAMES, get_disease_name

MODEL_TO_DISEASE = {"Corn_(maize)___Common_rust_": "Corn_(maize)__Common_rust"}


def canonical_code(model_label: str) -> str:
    if model_label not in CLASS_NAMES.values():
        raise ValueError(f"Unknown model label: {model_label}")
    return MODEL_TO_DISEASE.get(model_label, model_label)


def class_contract() -> dict:
    return {
        "version": "1.0",
        "identity": "Disease.code (exact lookup; no fuzzy mapping)",
        "classes": [
            {
                "class_id": i,
                "model_label": label,
                "disease_code": canonical_code(label),
                "disease_name": get_disease_name(label),
            }
            for i, label in CLASS_NAMES.items()
        ],
    }
