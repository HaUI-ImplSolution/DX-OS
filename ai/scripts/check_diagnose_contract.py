# SPDX-License-Identifier: Apache-2.0
"""Run with ``python -m scripts.check_diagnose_contract`` from ``ai/``."""

from datetime import date

from app.api.schemas import DiagnoseRequest
from app.clients.backend_client import BackendClientError, DiagnosisContext
from app.cv.model_profile import MODEL_NAME, MODEL_VERSION
from app.services.inference_service import run_stateless_diagnose


def main() -> None:
    events: list[str] = []

    class Predictor:
        def predict(self, _image: bytes) -> dict:
            events.append("cv")
            return {
                "disease_code": "Rice___Blast",
                "disease_name": "Bệnh đạo ôn trên lúa",
                "confidence": 0.94,
                "model_name": MODEL_NAME,
                "model_version": MODEL_VERSION,
            }

    class Backend:
        def get_diagnosis_context(self, **kwargs) -> DiagnosisContext:
            events.append("backend")
            assert kwargs == {
                "request_id": "req-1",
                "disease_code": "Rice___Blast",
                "crop_season_id": 101,
            }
            return DiagnosisContext(
                request_id="req-1",
                disease_code="Rice___Blast",
                disease_name="Bệnh đạo ôn trên lúa",
                rules=[],
                recent_treatments=[],
            )

    class Retriever:
        def retrieve(self, **kwargs) -> list[dict]:
            events.append("rag")
            assert kwargs["disease_code"] == "Rice___Blast"
            return [
                {
                    "content": "Ưu tiên biện pháp canh tác và quản lý nước hợp lý.",
                    "source_ids": ["SRC_IRRI_BLAST"],
                    "similarity": 0.91,
                }
            ]

    class Writer:
        def write(self, evidence: str) -> tuple[str, str]:
            assert evidence == "Ưu tiên biện pháp canh tác và quản lý nước hợp lý."
            return evidence, "evidence_fallback"

    payload = DiagnoseRequest.model_validate(
        {
            "request_id": "req-1",
            "crop_season_id": 101,
            "image_url": "http://backend:8081/uploads/leaf.jpg",
            "field_context": {
                "parcel_code": "P-01",
                "crop_name": "Lúa",
                "growth_stage": None,
                "recent_treatments": [
                    {
                        "pesticide_name": "Xử lý lịch sử",
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
        backend_client=Backend(),
        rag_retriever=Retriever(),
        recommendation_writer=Writer(),
    )
    body = response.model_dump(mode="json")

    assert events == ["cv", "backend", "rag"]
    assert body["structured_context"] == {
        "path": "review_required",
        "rules": [],
    }
    summary = body["recommendation"]["summary"]
    assert "Ưu tiên biện pháp canh tác và quản lý nước hợp lý." in summary
    assert body["recommendation"]["selected_rule_id"] is None
    assert "NEWTEC" not in summary
    assert "0.75" not in summary
    assert "1.0 L/ha" not in summary
    assert set(body) == {
        "request_id",
        "cv_result",
        "rag_evidence",
        "structured_context",
        "recommendation",
    }

    class BackendWithoutRule:
        def get_diagnosis_context(self, **_kwargs) -> DiagnosisContext:
            return DiagnosisContext(
                request_id="req-1",
                disease_code="Rice___Blast",
                disease_name="Bệnh đạo ôn trên lúa",
                rules=[],
                recent_treatments=[],
            )

    review_response = run_stateless_diagnose(
        payload=payload,
        image_bytes=b"image",
        predictor=Predictor(),
        db=object(),
        backend_client=BackendWithoutRule(),
        rag_retriever=Retriever(),
        recommendation_writer=Writer(),
    ).model_dump(mode="json")
    assert review_response["structured_context"] == {
        "path": "review_required",
        "rules": [],
    }
    assert review_response["recommendation"]["selected_rule_id"] is None
    assert (
        "Cần kỹ sư nông nghiệp đánh giá chuyên sâu"
        in review_response["recommendation"]["summary"]
    )

    failed_events: list[str] = []

    class FailurePredictor:
        def predict(self, _image: bytes) -> dict:
            failed_events.append("cv")
            return {
                "disease_code": "Rice___Blast",
                "disease_name": "Bệnh đạo ôn trên lúa",
                "confidence": 0.94,
                "model_name": MODEL_NAME,
                "model_version": MODEL_VERSION,
            }

    class UnavailableBackend:
        def get_diagnosis_context(self, **_kwargs) -> DiagnosisContext:
            failed_events.append("backend")
            raise BackendClientError("Backend diagnosis context is unavailable")

    class MustNotRetrieve:
        def retrieve(self, **_kwargs) -> list[dict]:
            failed_events.append("rag")
            raise AssertionError("RAG must not run without structured facts")

    class MustNotWrite:
        def write(self, _evidence: str) -> tuple[str, str]:
            failed_events.append("recommendation")
            raise AssertionError("Recommendation must not run without structured facts")

    try:
        run_stateless_diagnose(
            payload=payload,
            image_bytes=b"image",
            predictor=FailurePredictor(),
            db=object(),
            backend_client=UnavailableBackend(),
            rag_retriever=MustNotRetrieve(),
            recommendation_writer=MustNotWrite(),
        )
    except BackendClientError:
        pass
    else:
        raise AssertionError("Missing structured facts must fail the diagnosis")
    assert failed_events == ["cv", "backend"]
    print("Diagnose contract check: PASS")


if __name__ == "__main__":
    main()
