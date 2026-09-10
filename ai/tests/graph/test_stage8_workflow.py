# SPDX-License-Identifier: Apache-2.0
import pytest
from app.graph.graph import farm_graph, build_graph
from app.graph.state import FarmState


def mock_vision_tool(disease_code: str):
    def _tool(image_bytes: bytes):
        return {
            "disease_code": disease_code,
            "disease_name": disease_code.replace("___", " "),
            "confidence": 0.95,
            "model_name": "EfficientNet-B4-Test",
            "model_version": "v1.0-test",
        }
    return _tool


def test_resolve_request_validation():
    graph = build_graph()
    initial_state: FarmState = {
        "request_id": "",
        "image_ref": b"fake_image",
    }
    with pytest.raises(ValueError, match="request_id is required"):
        graph.invoke(initial_state)


def test_stage8_treatment_path_pending():
    graph = build_graph()
    initial_state: FarmState = {
        "request_id": "req-001",
        "parcel_code": "P-001",
        "image_ref": b"dummy_bytes",
        "cv_result": {
            "disease_code": "Tomato___Late_blight",
            "disease_name": "Cà chua - Bệnh sương mai",
            "confidence": 0.96,
            "model_name": "EfficientNet-B4",
            "model_version": "v1.0",
        },
        "treatment_rule": {
            "path": "treatment",
            "disease_code": "Tomato___Late_blight",
            "rules": [
                {
                    "id": "rule-101",
                    "pesticide": "Mancozeb 80WP",
                    "active_ingredient": "Mancozeb",
                    "dose_min": 2.0,
                    "dose_max": 2.5,
                    "dose_unit": "kg/ha",
                    "phi_days": 7,
                }
            ],
        },
        "approval_status": "PENDING",
    }

    final_state = graph.invoke(initial_state)

    assert final_state["approval_status"] == "PENDING"
    rec = final_state.get("recommendation")
    assert rec is not None
    assert rec["path"] == "treatment"
    assert len(rec["actions"]) == 1
    assert rec["actions"][0]["pesticide"] == "Mancozeb 80WP"


def test_stage8_policy_path_pending():
    graph = build_graph()
    initial_state: FarmState = {
        "request_id": "req-002",
        "parcel_code": "P-002",
        "image_ref": b"dummy_bytes",
        "cv_result": {
            "disease_code": "Tomato___Tomato_mosaic_virus",
            "disease_name": "Cà chua - Khảm lá Virus",
            "confidence": 0.92,
        },
        "treatment_rule": {
            "path": "policy",
            "disease_code": "Tomato___Tomato_mosaic_virus",
            "policy": {
                "management_status": "NO_CHEMICAL_CONTROL",
                "direct_treatment_rule_allowed": False,
                "notes": "Nhổ bỏ cây bệnh, vệ sinh công cụ, không phun thuốc hóa học.",
            },
        },
        "approval_status": "PENDING",
    }

    final_state = graph.invoke(initial_state)

    assert final_state["approval_status"] == "PENDING"
    rec = final_state.get("recommendation")
    assert rec is not None
    assert rec["path"] == "policy"
    assert len(rec["actions"]) == 0
    assert rec["policy"]["management_status"] == "NO_CHEMICAL_CONTROL"


def test_stage8_engineer_approval_approved():
    graph = build_graph()
    initial_state: FarmState = {
        "request_id": "req-003",
        "parcel_code": "P-003",
        "image_ref": b"dummy_bytes",
        "vision_tool": mock_vision_tool("Tomato___Late_blight"),
        "treatment_rule": {
            "path": "treatment",
            "disease_code": "Tomato___Late_blight",
            "rules": [
                {
                    "id": "rule-101",
                    "pesticide": "Mancozeb 80WP",
                    "dose_min": 2.0,
                    "dose_max": 2.5,
                    "dose_unit": "kg/ha",
                    "phi_days": 7,
                }
            ],
        },
        "approval_status": "APPROVED",
        "selected_rule_id": "rule-101",
        "approved_dose": 2.2,
    }

    final_state = graph.invoke(initial_state)

    assert final_state["approval_status"] == "APPROVED"
    assert final_state["application_status"] == "WAITING_APPLIED"
    assert final_state["response"]["status"] == "APPROVED"
    assert final_state["response"]["selected_rule_id"] == "rule-101"


def test_stage8_engineer_approval_rejected():
    graph = build_graph()
    initial_state: FarmState = {
        "request_id": "req-004",
        "parcel_code": "P-004",
        "image_ref": b"dummy_bytes",
        "vision_tool": mock_vision_tool("Tomato___Late_blight"),
        "approval_status": "REJECTED",
        "approval_reason": "Hình ảnh bị mờ, không đủ cơ sở chẩn đoán.",
    }

    final_state = graph.invoke(initial_state)

    assert final_state["approval_status"] == "REJECTED"
    assert final_state["application_status"] == "CANCELLED"
    assert final_state["response"]["status"] == "REJECTED"
    assert "mờ" in final_state["response"]["approval_reason"]


def test_stage8_engineer_approval_rejected_missing_reason():
    graph = build_graph()
    initial_state: FarmState = {
        "request_id": "req-005",
        "parcel_code": "P-005",
        "image_ref": b"dummy_bytes",
        "vision_tool": mock_vision_tool("Tomato___Late_blight"),
        "approval_status": "REJECTED",
        "approval_reason": "",
    }

    with pytest.raises(ValueError, match="approval_reason is required"):
        graph.invoke(initial_state)
