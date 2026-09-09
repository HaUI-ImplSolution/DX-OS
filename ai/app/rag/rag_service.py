# ai/app/rag/rag_service.py

from app.rag.prompt_builder import build_rag_prompt
from app.rag.retriever import RAGRetriever
from langchain_ollama import ChatOllama
from sqlalchemy.orm import Session


class RAGService:
    def __init__(
        self,
        model_name: str = "qwen2.5:1.5b",
    ):
        self.retriever = RAGRetriever()

        self.llm = ChatOllama(
            model=model_name,
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
            "sources": [
                {
                    "document_id": item["document_id"],
                    "chunk_id": item["chunk_id"],
                    "similarity": item["similarity"],
                }
                for item in contexts
            ],
        }