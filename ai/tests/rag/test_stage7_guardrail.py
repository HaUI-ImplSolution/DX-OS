# SPDX-License-Identifier: Apache-2.0
from types import SimpleNamespace

from app.rag.rag_service import RAGService


def test_unsupported_llm_facts_are_not_returned():
    service = RAGService.__new__(RAGService)
    context = {
        "content": "Vệ sinh vườn và loại bỏ lá bệnh.",
        "source_ids": ["S05"],
        "document_id": "doc",
        "chunk_id": "chunk",
        "similarity": 0.8,
    }
    service.retriever = SimpleNamespace(retrieve=lambda **kwargs: [context])
    service.llm = SimpleNamespace(
        invoke=lambda prompt: SimpleNamespace(
            content="Phun thuốc XYZ 2 L/ha, PHI 3 ngày."
        )
    )
    result = service.answer(None, "Phun thuốc gì?")
    assert result["answer"] == context["content"]
    assert "XYZ" not in result["answer"]
    assert result["sources"][0]["source_id"] == "S05"


def test_empty_evidence_does_not_call_llm():
    service = RAGService.__new__(RAGService)
    service.retriever = SimpleNamespace(retrieve=lambda **kwargs: [])
    result = service.answer(None, "unknown")
    assert result["sources"] == []
    assert result["answer"] == "Chưa có tài liệu phù hợp để trả lời."
