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

from typing import Any

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
    retriever = RAGRetriever()
    top_k: int = int(state.get("rag_top_k", 3))
    db = state.get("db")
    if db is None:
        # In minimal mode (e.g., unit tests) we skip actual DB retrieval.
        # Return an empty list so downstream nodes can still operate.
        contexts = []
    else:
        contexts = retriever.retrieve(
            db=db,
            query=query,
            top_k=top_k,
            disease_code=(state.get("cv_result") or {}).get("disease_code"),
        )

    # Store raw contexts for the next node
    state["rag_context"] = contexts
    return state
