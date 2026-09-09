# ai/app/rag/prompt_builder.py

def build_rag_prompt(
    question: str,
    contexts: list[dict],
) -> str:
    context_text = "\n\n".join(
        [
            f"[Nguồn {i+1}]\n{item['content']}"
            for i, item in enumerate(contexts)
        ]
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