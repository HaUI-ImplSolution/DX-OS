# SPDX-License-Identifier: Apache-2.0
from typing import Any


def build_rag_prompt(
    question: str,
    contexts: list[dict[str, Any]],
) -> str:
    context_text = "\n\n".join(
        _format_context(index, item) for index, item in enumerate(contexts, start=1)
    )

    return f"""
Bạn là trợ lý nông nghiệp của hệ thống DX-Farm.

Nhiệm vụ:
- Trả lời câu hỏi chỉ dựa trên phần NGỮ CẢNH được cung cấp.
- Không tự bịa thêm liều lượng thuốc, PHI, hoạt chất hoặc dữ liệu định lượng.
- Nếu ngữ cảnh không đủ để trả lời, hãy nói rõ rằng dữ liệu hiện tại chưa đủ.
- Trả lời ngắn gọn, dễ hiểu cho nông dân.

NGỮ CẢNH:
{context_text}

CÂU HỎI:
{question}

TRẢ LỜI:
""".strip()


def _format_context(index: int, item: dict[str, Any]) -> str:
    source_ids = item.get("source_ids") or []
    source_label = ", ".join(source_ids) if source_ids else str(index)
    return f"[Nguồn {source_label}]\n{item['content']}"
