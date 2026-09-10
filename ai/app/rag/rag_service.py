from typing import Any

from langchain_ollama import ChatOllama
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.rag.prompt_builder import build_rag_prompt
from app.rag.retriever import RAGRetriever
from app.rag.source_registry import load_source_registry


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
        self.llm = ChatOllama(
            model=model_name or settings.ollama_model,
            base_url=base_url or settings.ollama_base_url,
            temperature=0,
        )

    def answer(
        self,
        db: Session,
        question: str,
        top_k: int = 3,
    ) -> dict:
        contexts = self.retriever.retrieve(
            db=db,
            query=question,
            top_k=top_k,
        )
        prompt = build_rag_prompt(
            question=question,
            contexts=contexts,
        )
        response = self.llm.invoke(prompt)

        return {
            "question": question,
            "answer": response.content,
            "sources": build_citations(contexts),
        }
