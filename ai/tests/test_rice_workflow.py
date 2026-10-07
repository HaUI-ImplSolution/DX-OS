# SPDX-License-Identifier: Apache-2.0
from datetime import date

import pytest

from app.api.schemas import DiagnoseRequest
from app.clients.backend_client import DiagnosisContext
from app.cv.triage import CvTriageError
from app.rag.ingestion_service import load_disease_knowledge
from app.services.inference_service import run_stateless_diagnose

RICE_CODES = [
    "Rice___healthy",
    "Rice___Blast",
    "Rice___Brown_spot",
    "Rice___Leaf_smut",
    "Rice___Tungro",
    "Rice___Sheath_blight",
]


def test_rag_corpus_contains_exactly_six_canonical_rice_codes():
    documents = load_disease_knowledge()

    assert [document["disease_code"] for document in documents] == RICE_CODES
    assert all(document["review_status"] == "IN_REVIEW" for document in documents)
    assert all(document["provenance"]["source_ids"] for document in documents)


@pytest.mark.parametrize("document", load_disease_knowledge())
def test_each_rice_code_flows_through_rag_to_review_required(document):
    code = document["disease_code"]
    name = document["class_name_vi"]
    calls = []

    class Predictor:
        def predict(self, _image: bytes) -> dict:
            return {
                "disease_code": code,
                "disease_name": name,
                "confidence": 0.91,
                "model_name": "YOLOv8n-RiceLeafBD",
                "model_version": "3.0.0",
            }

    class BackendWithoutApprovedRule:
        def get_diagnosis_context(self, **kwargs) -> DiagnosisContext:
            calls.append(("backend", kwargs["disease_code"]))
            return DiagnosisContext(
                request_id="rice-workflow-test",
                disease_code=code,
                disease_name=name,
                rules=[],
                recent_treatments=[],
            )

    class Retriever:
        def retrieve(self, **kwargs) -> list[dict]:
            calls.append(("rag", kwargs["disease_code"]))
            assert kwargs["disease_code"] == code
            return [
                {
                    "content": document["content"],
                    "source_ids": document["provenance"]["source_ids"],
                    "similarity": 1.0,
                }
            ]

    class EvidenceWriter:
        def write(self, evidence: str) -> tuple[str, str]:
            assert evidence == document["content"]
            return "Ưu tiên hướng dẫn IPM trong tài liệu đã kiểm chứng.", "test"

    payload = DiagnoseRequest.model_validate(
        {
            "request_id": "rice-workflow-test",
            "crop_season_id": 1,
            "image_url": "http://backend:8081/uploads/rice-leaf.jpg",
            "field_context": {
                "parcel_code": "RICE-01",
                "crop_name": "Lúa",
                "growth_stage": None,
                "recent_treatments": [
                    {
                        "pesticide_name": "Lịch sử xử lý",
                        "applied_at": "2026-09-01T08:00:00Z",
                        "harvest_locked_until": date(2026, 9, 8),
                    }
                ],
            },
        }
    )

    response = run_stateless_diagnose(
        payload=payload,
        image_bytes=b"image",
        predictor=Predictor(),
        db=object(),
        backend_client=BackendWithoutApprovedRule(),
        rag_retriever=Retriever(),
        recommendation_writer=EvidenceWriter(),
    ).model_dump(mode="json")

    assert calls == [("backend", code), ("rag", code)]
    assert response["cv_result"]["disease_code"] == code
    assert (
        response["rag_evidence"][0]["source_ids"]
        == document["provenance"]["source_ids"]
    )
    assert response["structured_context"] == {
        "path": "review_required",
        "rules": [],
    }
    assert response["recommendation"]["selected_rule_id"] is None
    summary = response["recommendation"]["summary"]
    assert "Cần kỹ sư nông nghiệp đánh giá chuyên sâu" in summary
    assert "NEWTEC" not in summary
    assert "0.75" not in summary


def test_low_confidence_stops_before_backend_rag_and_llm():
    class LowConfidencePredictor:
        def predict(self, _image: bytes) -> dict:
            return {
                "disease_code": "Rice___Blast",
                "disease_name": "Bệnh đạo ôn trên lúa",
                "confidence": 0.69,
                "model_name": "YOLOv8n-RiceLeafBD",
                "model_version": "3.0.0",
            }

    class MustNotRun:
        def __getattr__(self, _name):
            raise AssertionError("Downstream dependency must not run after CV gate")

    payload = DiagnoseRequest.model_validate(
        {
            "request_id": "low-confidence-test",
            "crop_season_id": 1,
            "image_url": "http://backend:8081/uploads/rice-leaf.jpg",
            "field_context": {"parcel_code": "RICE-01"},
        }
    )

    with pytest.raises(CvTriageError, match="69.0%"):
        run_stateless_diagnose(
            payload=payload,
            image_bytes=b"image",
            predictor=LowConfidencePredictor(),
            db=object(),
            backend_client=MustNotRun(),
            rag_retriever=MustNotRun(),
            recommendation_writer=MustNotRun(),
        )
