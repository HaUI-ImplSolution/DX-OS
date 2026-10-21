# SPDX-License-Identifier: Apache-2.0
from datetime import date

import pytest
from app.api.schemas import DiagnoseRequest
from app.clients.backend_client import DiagnosisContext
from app.cv.model_profile import MODEL_NAME, MODEL_VERSION
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
@pytest.mark.parametrize("confidence", [0.70, 0.91])
def test_each_rice_code_flows_through_rag_to_review_required(document, confidence):
    code = document["disease_code"]
    name = document["class_name_vi"]
    calls = []

    class Predictor:
        def predict(self, _image: bytes) -> dict:
            return {
                "disease_code": code,
                "disease_name": name,
                "confidence": confidence,
                "model_name": MODEL_NAME,
                "model_version": MODEL_VERSION,
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
                    "document_title": document["disease_name"],
                    "chunk_index": 2,
                    "source_ids": document["provenance"]["source_ids"],
                    "similarity": 1.0,
                }
            ]

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
    ).model_dump(mode="json")

    assert calls == [("backend", code), ("rag", code)]
    assert response["cv_result"]["disease_code"] == code
    assert response["rag_evidence"][0]["document_title"] == document["disease_name"]
    assert response["rag_evidence"][0]["chunk_index"] == 2
    assert (
        response["rag_evidence"][0]["source_ids"]
        == document["provenance"]["source_ids"]
    )
    assert response["structured_context"] == {
        "path": "review_required",
        "rules": [],
    }
    assert response["recommendation"]["selected_rule_id"] is None


@pytest.mark.parametrize("confidence", [0.69, 0.6999])
def test_low_confidence_stops_before_backend_rag_and_llm(confidence):
    class LowConfidencePredictor:
        def predict(self, _image: bytes) -> dict:
            return {
                "disease_code": "Rice___Blast",
                "disease_name": "Bệnh đạo ôn trên lúa",
                "confidence": confidence,
                "model_name": MODEL_NAME,
                "model_version": MODEL_VERSION,
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

    with pytest.raises(CvTriageError, match="chưa đủ tin cậy"):
        run_stateless_diagnose(
            payload=payload,
            image_bytes=b"image",
            predictor=LowConfidencePredictor(),
            db=object(),
            backend_client=MustNotRun(),
            rag_retriever=MustNotRun(),
        )
