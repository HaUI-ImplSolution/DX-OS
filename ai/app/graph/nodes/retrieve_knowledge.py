# SPDX-License-Identifier: Apache-2.0
"""LangGraph node: retrieve_knowledge

This node fetches relevant knowledge chunks from the pgvector store via the
RAGRetriever (or RAGService) and stores them in the workflow state under
`rag_context`.

The node expects a question string in `state.get("rag_question")`. If the key
is missing we generate a simple question from the CV result, e.g.
`"Explain disease {disease_code}"`.

The function returns the updated state so downstream nodes can use the
retrieved context.
"""

import time
from typing import Any

from sqlalchemy.exc import SQLAlchemyError

from app.graph.state import FarmState  # type: ignore
from app.rag.retriever import RAGRetriever


def retrieve_knowledge(state: FarmState) -> dict[str, Any]:
    """Retrieve knowledge chunks and attach them to the state.

    Args:
        state: The LangGraph state dictionary.

    Returns:
        Updated state with a new key ``rag_context`` containing a list of
        dictionaries, each with ``document_id``, ``chunk_id``, ``content`` and
        ``similarity``.
    """
    # Determine the query – prefer an explicit question if provided
    query: str = state.get("rag_question")
    if not query:
        # Fallback: build a generic question from CV result if available
        cv = state.get("cv_result") or {}
        disease_code = cv.get("disease_code", "")
        if disease_code:
            query = f"Explain disease {disease_code} in detail"
        else:
            query = "Explain the relevant agricultural knowledge"

    # Instantiate retriever and determine top_k
    top_k: int = int(state.get("rag_top_k") or 3)
    if not 1 <= top_k <= 20:
        raise ValueError("rag_top_k must be between 1 and 20")
    db = state.get("db")
    tool = state.get("rag_tool")
    started = time.perf_counter()
    try:
        if tool is not None:
            contexts = tool(
                query,
                (state.get("cv_result") or {}).get("disease_code"),
                top_k,
            )
        elif db is None:
            contexts = state.get("rag_context") or []
        else:
            contexts = RAGRetriever().retrieve(
                db=db,
                query=query,
                top_k=top_k,
                disease_code=(state.get("cv_result") or {}).get("disease_code"),
            )
    except (RuntimeError, ConnectionError, TimeoutError, OSError, SQLAlchemyError):
        contexts = []
        state.setdefault("errors", []).append(
            "RAG_UNAVAILABLE: structured recommendation remains available"
        )
    state.setdefault("timings_ms", {})["rag_retrieval"] = round(
        (time.perf_counter() - started) * 1000, 2
    )

    # Store raw contexts for the next node
    state["rag_context"] = contexts
    return state
