"""Run with ``python -m scripts.check_diagnose_contract`` from ``ai/``."""

from datetime import date
from decimal import Decimal

from app.api.schemas import DiagnoseRequest
from app.clients.backend_client import BackendClientError, DiagnosisContext, RuleFact
from app.core.config import get_settings
from app.services.inference_service import run_stateless_diagnose


def main() -> None:
    events: list[str] = []
    threshold = get_settings().cv_confidence_threshold
    if not 0 < threshold < 1:
        raise AssertionError("CV_CONFIDENCE_THRESHOLD must be between 0 and 1")

    class Predictor:
        confidence = min(1.0, threshold + 0.1)

        def predict(self, _image: bytes) -> dict:
            events.append("cv")
            return {
                "disease_code": "Tomato___Late_blight",
                "disease_name": "Bệnh mốc sương cà chua",
                "confidence": self.confidence,
                "model_name": "EfficientNet-B4",
                "model_version": "v1.0",
            }

    class Backend:
        def get_diagnosis_context(self, **kwargs) -> DiagnosisContext:
            events.append("backend")
            assert kwargs == {
                "request_id": "req-1",
                "disease_code": "Tomato___Late_blight",
                "crop_season_id": 101,
            }
            return DiagnosisContext(
                request_id="req-1",
                disease_code="Tomato___Late_blight",
                disease_name="Bệnh mốc sương cà chua",
                rules=[
                    RuleFact(
                        rule_id="7",
                        pesticide="Copper 50WP",
                        dose_min=Decimal("1.0"),
                        dose_max=Decimal("1.5"),
                        dose_unit="kg/ha",
                        phi_days=7,
                        version="v1",
                    )
                ],
                recent_treatments=[],
            )

    class Retriever:
        def retrieve(self, **kwargs) -> list[dict]:
            events.append("rag")
            assert kwargs["disease_code"] == "Tomato___Late_blight"
            return [
                {
                    "content": "Bằng chứng đã được ingest.",
                    "source_ids": ["DOC-1"],
                    "similarity": 0.91,
                }
            ]

    class Writer:
        def write(self, evidence: str) -> tuple[str, str]:
            assert evidence == "Bằng chứng đã được ingest."
            return evidence, "evidence_fallback"

    payload = DiagnoseRequest.model_validate(
        {
            "request_id": "req-1",
            "crop_season_id": 101,
            "image_url": "http://backend:8081/uploads/leaf.jpg",
            "field_context": {
                "parcel_code": "P-01",
                "crop_name": "Cà chua",
                "growth_stage": None,
                "recent_treatments": [
                    {
                        "pesticide_name": "Copper 50WP",
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
    assert body["structured_context"]["rules"][0] == {
        "rule_id": "7",
        "pesticide": "Copper 50WP",
        "dose_min": 1.0,
        "dose_max": 1.5,
        "dose_unit": "kg/ha",
        "phi_days": 7,
        "version": "v1",
    }
    summary = body["recommendation"]["summary"]
    assert "Copper 50WP" in summary
    assert "Bằng chứng đã được ingest." in summary
    assert body["recommendation"]["selected_rule_id"] == "7"
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
                disease_code="Tomato___Late_blight",
                disease_name="Bệnh mốc sương cà chua",
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

    class BackendWithManyRules(Backend):
        def get_diagnosis_context(self, **kwargs) -> DiagnosisContext:
            context = super().get_diagnosis_context(**kwargs)
            return context.model_copy(
                update={
                    "rules": [
                        *context.rules,
                        RuleFact(
                            rule_id="8",
                            pesticide="Alternative Copper",
                            dose_min=Decimal("0.8"),
                            dose_max=Decimal("1.0"),
                            dose_unit="kg/ha",
                            phi_days=10,
                            version="v1",
                        ),
                    ]
                }
            )

    multiple_response = run_stateless_diagnose(
        payload=payload,
        image_bytes=b"image",
        predictor=Predictor(),
        db=object(),
        backend_client=BackendWithManyRules(),
        rag_retriever=Retriever(),
        recommendation_writer=Writer(),
    ).model_dump(mode="json")
    assert multiple_response["structured_context"]["path"] == "review_required"
    assert len(multiple_response["structured_context"]["rules"]) == 2
    assert multiple_response["recommendation"]["selected_rule_id"] is None
    assert "không tự chọn theo thứ tự" in multiple_response["recommendation"]["summary"]

    class LowConfidencePredictor(Predictor):
        confidence = threshold - 0.01

    low_response = run_stateless_diagnose(
        payload=payload,
        image_bytes=b"image",
        predictor=LowConfidencePredictor(),
        db=object(),
        backend_client=Backend(),
        rag_retriever=Retriever(),
        recommendation_writer=Writer(),
    ).model_dump(mode="json")
    assert low_response["structured_context"]["path"] == "low_confidence_review"
    assert low_response["recommendation"]["selected_rule_id"] is None
    assert "chưa đạt ngưỡng an toàn" in low_response["recommendation"]["summary"]

    class ThresholdPredictor(Predictor):
        confidence = threshold

    threshold_response = run_stateless_diagnose(
        payload=payload,
        image_bytes=b"image",
        predictor=ThresholdPredictor(),
        db=object(),
        backend_client=Backend(),
        rag_retriever=Retriever(),
        recommendation_writer=Writer(),
    ).model_dump(mode="json")
    assert threshold_response["structured_context"]["path"] == "treatment"
    assert threshold_response["recommendation"]["selected_rule_id"] == "7"

    failed_events: list[str] = []

    class FailurePredictor:
        def predict(self, _image: bytes) -> dict:
            failed_events.append("cv")
            return {
                "disease_code": "Tomato___Late_blight",
                "disease_name": "Bệnh mốc sương cà chua",
                "confidence": 0.94,
                "model_name": "EfficientNet-B4",
                "model_version": "v1.0",
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
    print(
        "Diagnose contract check: PASS "
        f"(threshold={threshold:.2f}, model=EfficientNet-B4 v1.0, "
        "paths=low_confidence_review/review_required/treatment)"
    )


if __name__ == "__main__":
    main()
