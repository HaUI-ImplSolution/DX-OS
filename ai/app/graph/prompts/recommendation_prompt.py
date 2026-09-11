# SPDX-License-Identifier: Apache-2.0
"""Prompt for extractive, evidence-only recommendation wording."""

GROUNDED_EXCERPT_INSTRUCTION = (
    "Chọn một đoạn nguyên văn ngắn, hữu ích nhất từ EVIDENCE. "
    "Không thêm thuốc, hoạt chất, liều hoặc PHI. Chỉ trả đoạn đã chọn."
)


def build_grounded_excerpt_prompt(evidence: str) -> str:
    """Build the prompt without mixing structured treatment facts into the LLM input."""

    return f"{GROUNDED_EXCERPT_INSTRUCTION}\n\nEVIDENCE:\n{evidence}"
