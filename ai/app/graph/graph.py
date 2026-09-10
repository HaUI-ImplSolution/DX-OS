# SPDX-License-Identifier: Apache-2.0
"""Minimal LangGraph construction for DX‑Farm MVP.

We only build the nodes that have concrete implementations (the ones we need for the
spine flow). Nodes that are still TODO are kept as placeholders – they will be
executed but simply return the received state.
"""

from typing import Callable

from langgraph.graph import StateGraph, END

from app.graph.state import FarmState  # type: ignore
from app.graph.nodes import (
    resolve_request,
    cv_diagnosis,
    load_field_context,
    treatment_lookup,
    retrieve_knowledge,
    generate_recommendation,
    engineer_approval,
    record_treatment,
    reject_case,
)


def _passthrough(state: FarmState) -> FarmState:
    """Placeholder for nodes that are not yet implemented.
    They simply return the state unchanged so the graph can run.
    """
    return state


def build_graph() -> Callable[[FarmState], FarmState]:
    """Construct and compile the StateGraph.

    Returns a callable that takes an initial ``FarmState`` and returns the final
    state after traversing the graph.
    """
    builder = StateGraph(FarmState)
    # Register nodes – use actual implementation where available, otherwise a
    # passthrough.
    builder.add_node("resolve_request", resolve_request)
    builder.add_node("cv_diagnosis", cv_diagnosis)
    builder.add_node("load_field_context", load_field_context)
    builder.add_node("treatment_lookup", treatment_lookup)
    builder.add_node("retrieve_knowledge", retrieve_knowledge)
    builder.add_node("generate_recommendation", generate_recommendation)
    builder.add_node("engineer_approval", engineer_approval)
    builder.add_node("record_treatment", record_treatment)
    builder.add_node("reject_case", reject_case)

    # Entry point
    builder.set_entry_point("resolve_request")

    # Define edges – minimal linear flow plus conditional approvals.
    builder.add_edge("resolve_request", "cv_diagnosis")
    builder.add_edge("cv_diagnosis", "load_field_context")
    builder.add_edge("load_field_context", "treatment_lookup")
    builder.add_edge("treatment_lookup", "retrieve_knowledge")
    builder.add_edge("retrieve_knowledge", "generate_recommendation")
    builder.add_edge("generate_recommendation", "engineer_approval")
    def route_approval(state: FarmState) -> str:
        status = state.get("approval_status", "PENDING")
        if status == "APPROVED":
            return "record_treatment"
        elif status == "REJECTED":
            return "reject_case"
        return END

    # Conditional branches from approval
    builder.add_conditional_edges(
        "engineer_approval",
        route_approval,
        {
            "record_treatment": "record_treatment",
            "reject_case": "reject_case",
            END: END,
        },
    )
    builder.add_edge("record_treatment", END)
    builder.add_edge("reject_case", END)

    graph = builder.compile()
    return graph


# Pre‑compile graph for FastAPI reuse
farm_graph = build_graph()

#
# Graph flow:
#   START
#     -> resolve_request
#     -> cv_diagnosis
#     -> load_field_context
#     -> treatment_lookup
#     -> retrieve_knowledge
#     -> generate_recommendation
#     -> engineer_approval  (human-in-the-loop interrupt)
#         APPROVED -> record_treatment -> END
#         REJECTED -> reject_case     -> END
#
# Ví dụ khung:
# from langgraph.graph import StateGraph, END
# from app.graph.state import FarmState
# from app.graph.nodes import (
#     resolve_request, cv_diagnosis, load_field_context,
#     treatment_lookup, retrieve_knowledge, generate_recommendation,
#     engineer_approval, record_treatment, reject_case,
# )
#
# def build_graph():
#     builder = StateGraph(FarmState)
#     builder.add_node("resolve_request", resolve_request)
#     # ... thêm các nodes khác
#     builder.set_entry_point("resolve_request")
#     # ... thêm các edges
#     return builder.compile()
#
# farm_graph = build_graph()
