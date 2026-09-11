import pytest

from app.graph.context import WorkflowRuntime
from app.graph.graph import build_graph
from app.graph.state import FarmState


class Writer:
    def write(self, evidence):
        return evidence, "test_grounded"


def vision(disease_code: str, confidence: float = 0.95):
    def call(_image: bytes):
        return {
            "disease_code": disease_code,
            "disease_name": disease_code.replace("___", " "),
            "confidence": confidence,
            "model_name": "EfficientNet-B4-Test",
            "model_version": "v1.0-test",
        }

    return call


def base_state(code: str, lookup: dict, confidence: float = 0.95) -> FarmState:
    return {
        "request_id": "req-stage8",
        "parcel_code": "P-001",
        "image_ref": b"image",
        "vision_tool": vision(code, confidence),
        "field_context_tool": lambda parcel, season: {
            "parcel": {"code": parcel},
            "current_season": {"id": season or "season"},
            "harvest": {"is_blocked": False, "locked_until": None},
        },
        "treatment_tool": lambda disease_code: lookup,
        "rag_tool": lambda query, disease_code, top_k: [
            {
                "content": f"Evidence for {disease_code}",
                "disease_code": disease_code,
                "source_ids": ["S05"],
                "score": 0.9,
            }
        ][:top_k],
        "recommendation_writer": Writer(),
        "approval_status": "PENDING",
        "errors": [],
        "timings_ms": {},
    }


def invoke_with_runtime(state: FarmState):
    runtime = WorkflowRuntime(
        vision_tool=state.pop("vision_tool"),
        field_context_tool=state.pop("field_context_tool"),
        treatment_tool=state.pop("treatment_tool"),
        rag_tool=state.pop("rag_tool"),
        recommendation_writer=state.pop("recommendation_writer"),
    )
    return build_graph().invoke(state, context=runtime)


def treatment_lookup():
    return {
        "path": "treatment",
        "disease_code": "Tomato___Late_blight",
        "rules": [
            {
                "id": "rule-101",
                "pesticide": "SOURCE PRODUCT",
                "active_ingredient": "SOURCE INGREDIENT",
                "dose_min": 2.0,
                "dose_max": 2.5,
                "dose_unit": "kg/ha",
                "phi_days": 7,
            }
        ],
    }


def test_resolve_request_validation():
    with pytest.raises(ValueError, match="request_id is required"):
        build_graph().invoke({"request_id": "", "image_ref": b"image"})


def test_treatment_path_reaches_pending_with_provenance():
    state = base_state("Tomato___Late_blight", treatment_lookup())
    final = invoke_with_runtime(state)
    assert "approval_status" not in final
    assert final["disease_code"] == "Tomato___Late_blight"
    assert final["field_context"]["parcel"]["code"] == "P-001"
    assert final["recommendation"]["path"] == "treatment"
    assert final["recommendation"]["actions"][0]["pesticide"] == "SOURCE PRODUCT"
    assert final["recommendation"]["rag_sources"] == ["S05"]
    assert final["recommendation"]["generation_mode"] == "test_grounded"
    assert final["recommendation"]["requires_engineer_approval"] is True


def test_policy_path_never_creates_chemical_action():
    lookup = {
        "path": "policy",
        "disease_code": "Tomato___Tomato_mosaic_virus",
        "rules": [],
        "policy": {
            "management_status": "NO_CHEMICAL_CONTROL",
            "direct_treatment_rule_allowed": False,
            "notes": "Remove infected plants.",
        },
    }
    final = invoke_with_runtime(base_state("Tomato___Tomato_mosaic_virus", lookup))
    assert final["recommendation"]["path"] == "policy"
    assert final["recommendation"]["actions"] == []
    assert (
        final["recommendation"]["policy"]["management_status"] == "NO_CHEMICAL_CONTROL"
    )


def test_review_required_and_low_confidence_are_visible():
    lookup = {
        "path": "review_required",
        "disease_code": "Tomato___healthy",
        "rules": [],
        "policy": None,
    }
    final = invoke_with_runtime(base_state("Tomato___healthy", lookup, confidence=0.2))
    assert final["low_confidence"] is True
    assert final["recommendation"]["low_confidence"] is True
    assert final["recommendation"]["path"] == "review_required"


def test_rag_unavailable_keeps_structured_facts():
    state = base_state("Tomato___Late_blight", treatment_lookup())

    def unavailable(*args):
        raise RuntimeError("offline")

    state["rag_tool"] = unavailable
    final = invoke_with_runtime(state)
    assert final["recommendation"]["actions"][0]["dose_min"] == 2.0
    assert final["recommendation"]["rag_sources"] == []
    assert final["errors"] == [
        "RAG_UNAVAILABLE: structured recommendation remains available"
    ]


def test_recommendation_graph_excludes_durable_transition_nodes():
    graph = build_graph().get_graph()
    assert "engineer_approval" not in graph.nodes
    assert "record_treatment" not in graph.nodes
    assert "reject_case" not in graph.nodes


def test_recommendation_graph_receives_services_from_runtime_context():
    state = base_state("Tomato___Late_blight", treatment_lookup())
    final = invoke_with_runtime(state)

    assert final["recommendation"]["path"] == "treatment"
