# SPDX-License-Identifier: Apache-2.0
"""Recommendation-only LangGraph construction for the DX-Farm MVP."""

from langgraph.graph import END, StateGraph

from src.graph.context import WorkflowRuntime
from src.graph.nodes import (
    cv_diagnosis,
    generate_recommendation,
    load_field_context,
    resolve_request,
    retrieve_knowledge,
    treatment_lookup,
)
from src.graph.state import FarmState  # type: ignore


def build_graph():
    """Construct and compile the StateGraph.

    The graph computes a recommendation and always ends while it is pending.
    Durable approval, rejection and application transitions are intentionally
    outside LangGraph and are owned by ``workflow_service`` / PostgreSQL.
    """
    builder = StateGraph(FarmState, context_schema=WorkflowRuntime)
    builder.add_node("resolve_request", resolve_request)
    builder.add_node("cv_diagnosis", cv_diagnosis)
    builder.add_node("load_field_context", load_field_context)
    builder.add_node("treatment_lookup", treatment_lookup)
    builder.add_node("retrieve_knowledge", retrieve_knowledge)
    builder.add_node("generate_recommendation", generate_recommendation)

    builder.set_entry_point("resolve_request")

    # The graph stops before human action. PostgreSQL is the source of truth
    # for the durable HITL transitions that follow.
    builder.add_edge("resolve_request", "cv_diagnosis")
    builder.add_edge("cv_diagnosis", "load_field_context")
    builder.add_edge("load_field_context", "treatment_lookup")
    builder.add_edge("treatment_lookup", "retrieve_knowledge")
    builder.add_edge("retrieve_knowledge", "generate_recommendation")
    builder.add_edge("generate_recommendation", END)

    return builder.compile()


# Pre-compile graph for FastAPI reuse.
farm_graph = build_graph()
