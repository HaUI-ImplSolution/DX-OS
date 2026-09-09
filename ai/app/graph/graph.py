# SPDX-License-Identifier: Apache-2.0
# TODO: Build và compile LangGraph StateGraph cho DX-Farm spine flow.
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
