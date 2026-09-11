import pytest

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
    final = build_graph().invoke(state)
    assert final["approval_status"] == "PENDING"
    assert final["disease_code"] == "Tomato___Late_blight"
    assert final["field_context"]["parcel"]["code"] == "P-001"
    assert final["recommendation"]["path"] == "treatment"
    assert final["recommendation"]["actions"][0]["pesticide"] == "SOURCE PRODUCT"
    assert final["recommendation"]["rag_sources"] == ["S05"]
    assert final["recommendation"]["generation_mode"] == "test_grounded"


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
    final = build_graph().invoke(base_state("Tomato___Tomato_mosaic_virus", lookup))
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
    final = build_graph().invoke(base_state("Tomato___healthy", lookup, confidence=0.2))
    assert final["low_confidence"] is True
    assert final["recommendation"]["low_confidence"] is True
    assert final["recommendation"]["path"] == "review_required"


def test_rag_unavailable_keeps_structured_facts():
    state = base_state("Tomato___Late_blight", treatment_lookup())

    def unavailable(*args):
        raise RuntimeError("offline")

    state["rag_tool"] = unavailable
    final = build_graph().invoke(state)
    assert final["recommendation"]["actions"][0]["dose_min"] == 2.0
    assert final["recommendation"]["rag_sources"] == []
    assert final["errors"] == [
        "RAG_UNAVAILABLE: structured recommendation remains available"
    ]


def test_approval_and_reject_routes_remain_distinct():
    approved = base_state("Tomato___Late_blight", treatment_lookup())
    approved.update(
        {
            "approval_status": "APPROVED",
            "selected_rule_id": "rule-101",
            "approved_dose": 2.2,
        }
    )
    approved_final = build_graph().invoke(approved)
    assert approved_final["application_status"] == "WAITING_APPLIED"
    assert "harvest_locked_until" not in approved_final["response"]

    rejected = base_state("Tomato___Late_blight", treatment_lookup())
    rejected.update({"approval_status": "REJECTED", "approval_reason": "Image unclear"})
    rejected_final = build_graph().invoke(rejected)
    assert rejected_final["application_status"] == "CANCELLED"
    assert rejected_final["response"]["approval_reason"] == "Image unclear"
