"""Stage 10 local smoke test with real CV startup and a pending graph run."""

import argparse
import json
import time
from pathlib import Path

from fastapi.testclient import TestClient
from sqlalchemy import func, select

from app.db.models import (
    Disease,
    DiseaseManagementPolicy,
    Document,
    DocumentChunk,
    Pesticide,
    TreatmentRule,
)
from app.db.repositories.parcel_repository import (
    get_current_crop_season,
    get_parcel_by_code,
)
from app.db.session import SessionLocal
from app.graph.graph import farm_graph
from app.llm.recommendation_writer import GroundedRecommendationWriter
from app.main import create_app
from app.rag.retriever import RAGRetriever
from app.services.structured_knowledge_service import lookup_structured_knowledge
from scripts.seed_demo_fixtures import PARCEL_CODE


def _args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", type=Path, required=True)
    return parser.parse_args()


def main() -> None:
    args = _args()
    if not args.image.is_file():
        raise SystemExit(f"Image does not exist: {args.image}")
    image_bytes = args.image.read_bytes()
    result: dict = {"checks": {}}

    with SessionLocal() as db:
        result["checks"]["database"] = "PASS"
        expected = {
            "diseases": (Disease, 38),
            "pesticides": (Pesticide, 4),
            "treatment_rules": (TreatmentRule, 8),
            "policies": (DiseaseManagementPolicy, 21),
            "rag_documents": (Document, 38),
            "rag_chunks": (DocumentChunk, 270),
        }
        for name, (model, count) in expected.items():
            actual = db.scalar(select(func.count()).select_from(model))
            if actual != count:
                raise SystemExit(f"{name}: expected {count}, got {actual}")
        result["checks"]["data_counts"] = "PASS"

        treatment = lookup_structured_knowledge(db, "Tomato___Late_blight")
        policy = lookup_structured_knowledge(db, "Tomato___Tomato_mosaic_virus")
        if treatment["path"] != "treatment" or not treatment["rules"]:
            raise SystemExit("TreatmentRule lookup failed")
        if (
            policy["path"] != "policy"
            or policy["policy"]["management_status"] != "NO_CHEMICAL_CONTROL"
        ):
            raise SystemExit("Policy lookup failed")
        result["checks"]["structured_lookup"] = "PASS"

        rag = RAGRetriever().retrieve(
            db,
            "Triệu chứng bệnh sương mai cà chua",
            disease_code="Tomato___Late_blight",
            top_k=3,
        )
        if not rag or not all(row["source_ids"] for row in rag):
            raise SystemExit("RAG retrieval/provenance failed")
        result["checks"]["rag"] = "PASS"

        parcel = get_parcel_by_code(db, PARCEL_CODE)
        if parcel is None:
            raise SystemExit("Run python -m scripts.seed_demo_fixtures first")
        season = get_current_crop_season(db, parcel.id)
        if season is None:
            raise SystemExit("Demo fixture has no active crop season")

        app = create_app()
        with TestClient(app) as client:
            health = client.get("/api/v1/disease/health")
            if health.status_code != 200 or not health.json()["model_loaded"]:
                raise SystemExit("CV health failed")
            cv_started = time.perf_counter()
            prediction_response = client.post(
                "/api/v1/disease/predict",
                files={
                    "image": (
                        args.image.name,
                        image_bytes,
                        "image/jpeg",
                    )
                },
            )
            if prediction_response.status_code != 200:
                raise SystemExit("Real CV prediction failed")
            prediction = prediction_response.json()
            result.setdefault("timings_ms", {})["real_cv_http"] = round(
                (time.perf_counter() - cv_started) * 1000, 2
            )
        result["checks"]["real_cv_health_prediction"] = "PASS"
        result["cv_disease_code"] = prediction["disease_code"]

        structured = lookup_structured_knowledge(db, prediction["disease_code"])
        graph_started = time.perf_counter()
        graph_result = farm_graph.invoke(
            {
                "request_id": "smoke-no-persist",
                "parcel_code": parcel.code,
                "crop_season_id": str(season.id),
                "image_ref": image_bytes,
                "cv_result": prediction,
                "db": db,
                "treatment_rule": structured,
                "recommendation_writer": GroundedRecommendationWriter(),
                "approval_status": "PENDING",
                "errors": [],
                "timings_ms": {},
            }
        )
        if graph_result["approval_status"] != "PENDING":
            raise SystemExit("Graph did not reach approval checkpoint")
        if graph_result["recommendation"]["path"] != structured["path"]:
            raise SystemExit("RAG/LLM changed structured path")
        result.setdefault("timings_ms", {})["graph_total"] = round(
            (time.perf_counter() - graph_started) * 1000, 2
        )
        result["graph_timings_ms"] = graph_result.get("timings_ms", {})
        result["checks"]["graph_to_pending"] = "PASS"
        result["llm_mode"] = graph_result["recommendation"]["generation_mode"]

    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
