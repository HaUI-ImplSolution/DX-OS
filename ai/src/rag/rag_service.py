from __future__ import annotations
from typing import Any

import httpx
from sqlalchemy.orm import Session

from src.core.config import get_settings
from src.rag.prompt_builder import build_rag_prompt
from src.rag.retriever import RAGRetriever
from src.rag.source_registry import load_source_registry


def build_citations(contexts: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Resolve unique source IDs in retrieval order to auditable citations."""

    registry = load_source_registry()
    citations: list[dict[str, Any]] = []
    seen: set[str] = set()

    for item in contexts:
        for source_id in item.get("source_ids", []):
            if source_id in seen:
                continue
            seen.add(source_id)

            source = registry[source_id]
            citations.append(
                {
                    "source_id": source_id,
                    "title": source["title"],
                    "organization": source["organization"],
                    "url": source["url"],
                    "authority_level": source["authority_level"],
                    "accessed_at": source["accessed_at"],
                    "document_id": item["document_id"],
                    "chunk_id": item["chunk_id"],
                    "similarity": item["similarity"],
                }
            )

    return citations


class RAGService:
    def __init__(
        self,
        model_name: str | None = None,
        base_url: str | None = None,
    ):
        settings = get_settings()
        self.retriever = RAGRetriever()
        self.model_name = model_name or getattr(settings, "ollama_model", "llama3")
        self.base_url = base_url or getattr(settings, "ollama_base_url", "http://localhost:11434")

    def answer(
        self,
        db: Session,
        question: str,
        top_k: int = 3,
        disease_code: str | None = None,
    ) -> dict:
        contexts = self.retriever.retrieve(
            db=db,
            query=question,
            top_k=top_k,
            disease_code=disease_code,
        )
        prompt = build_rag_prompt(
            question=question,
            contexts=contexts,
        )
        evidence = "\n\n".join(item["content"] for item in contexts)
        fallback = evidence or "Chưa có tài liệu phù hợp để trả lời."
        answer = fallback
        if contexts:
            try:
                url = f"{self.base_url.rstrip('/')}/api/generate"
                resp = httpx.post(url, json={"model": self.model_name, "prompt": prompt, "stream": False}, timeout=10.0)
                if resp.status_code == 200:
                    draft = resp.json().get("response", "")
                    if isinstance(draft, str) and draft.strip() and draft.strip() in evidence:
                        answer = draft.strip()
            except Exception:
                answer = fallback

        return {
            "question": question,
            "answer": answer,
            "answer_mode": "grounded_excerpt",
            "sources": build_citations(contexts),
        }


