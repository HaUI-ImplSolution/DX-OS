# SPDX-License-Identifier: Apache-2.0
import os
import uuid
from datetime import date

import pytest
from app.api.router import router
from app.cv.contract import class_contract
from app.db.models import (
    CropSeason,
    Diagnosis,
    Disease,
    DiseaseReport,
    Document,
    DocumentChunk,
    Parcel,
)
from app.db.session import engine, get_db
from app.rag.ingestion_service import VERSION, load_disease_knowledge
from app.rag.rag_service import build_citations
from app.rag.retriever import RAGRetriever
from app.services.diagnosis_service import diagnose_report
from app.services.plant_disease_service import PlantDiseaseService
from app.services.structured_knowledge_service import lookup_structured_knowledge
from fastapi import FastAPI
from fastapi.testclient import TestClient
from sqlalchemy import func, select, text
from sqlalchemy.orm import Session
from tests.cv.test_stage6_contract import Model, image_bytes

pytestmark = pytest.mark.skipif(
    os.getenv("RUN_STAGE67_DB_TESTS") != "1",
    reason="Requires migrated and ingested PostgreSQL.",
)


@pytest.fixture
def db():
    # Even service.commit() stays inside a rollback-only outer test transaction.
    with engine.connect() as connection:
        transaction = connection.begin()
        with Session(
            bind=connection, join_transaction_mode="create_savepoint"
        ) as session:
            yield session
        transaction.rollback()


@pytest.fixture
def report(db):
    parcel = Parcel(code=f"test-{uuid.uuid4().hex[:12]}", name="test", area_ha=1)
    db.add(parcel)
    db.flush()
    season = CropSeason(
        parcel_id=parcel.id,
        season_name="test",
        rice_variety="test",
        start_date=date(2026, 9, 10),
    )
    db.add(season)
    db.flush()
    report = DiseaseReport(crop_season_id=season.id, image_url="test://image")
    db.add(report)
    db.commit()
    return report


def test_all_cv_codes_match_database(db):
    assert {r["disease_code"] for r in class_contract()["classes"]} == set(
        db.scalars(select(Disease.code))
    )


@pytest.mark.parametrize(
    "index,path",
    [
        (30, "treatment"),
        (8, "policy"),
        (34, "policy"),
        (36, "policy"),
        (37, "review_required"),
    ],
)
def test_prediction_persists_and_looks_up(db, report, index, path):
    result = diagnose_report(
        db, report.id, image_bytes(), PlantDiseaseService(Model(index))
    )
    diagnosis = db.get(Diagnosis, uuid.UUID(result["diagnosis_id"]))
    assert diagnosis.confidence == result["prediction"]["confidence"]
    assert diagnosis.model_name == result["prediction"]["model_name"]
    assert diagnosis.model_version == result["prediction"]["model_version"]
    assert result["structured"]["path"] == path
    assert db.get(DiseaseReport, report.id).status == "ANALYZED"
    if path != "treatment":
        assert result["structured"]["rules"] == []
    if index == 36:
        assert (
            result["structured"]["policy"]["management_status"] == "NO_CHEMICAL_CONTROL"
        )
    with pytest.raises(FileExistsError):
        diagnose_report(db, report.id, image_bytes(), PlantDiseaseService(Model(index)))
    assert (
        db.scalar(
            select(func.count())
            .select_from(Diagnosis)
            .where(Diagnosis.disease_report_id == report.id)
        )
        == 1
    )


def test_unknown_code_rolls_back(db, report):
    class BadPredictor:
        def predict(self, image):
            return {
                "disease_code": "unknown",
                "confidence": 0.9,
                "model_name": "test",
                "model_version": "test",
            }

    with pytest.raises(ValueError):
        diagnose_report(db, report.id, b"image", BadPredictor())
    assert db.get(DiseaseReport, report.id).status == "PENDING"
    assert (
        db.scalar(select(Diagnosis).where(Diagnosis.disease_report_id == report.id))
        is None
    )


def test_diagnose_endpoint(db, report):
    app = FastAPI()
    app.include_router(router, prefix="/api/v1")
    app.state.plant_model = Model(30)
    app.dependency_overrides[get_db] = lambda: db
    client = TestClient(app)
    response = client.post(
        f"/api/v1/disease/reports/{report.id}/diagnose",
        files={"image": ("leaf.png", image_bytes(), "image/png")},
    )
    assert response.status_code == 200, response.text
    assert response.json()["structured"]["rules"]


def test_rich_corpus_metadata_and_duplicates(db):
    docs = db.scalars(
        select(Document).where(Document.document_type == "disease_knowledge")
    ).all()
    assert len(docs) == 38
    assert {d.disease_code for d in docs} == {
        d["disease_code"] for d in load_disease_knowledge()
    }
    assert all(d.version == VERSION and d.source for d in docs)
    assert not db.execute(
        text(
            "SELECT document_id, chunk_index FROM document_chunks "
            "GROUP BY document_id, chunk_index HAVING count(*) > 1"
        )
    ).all()
    assert not db.execute(
        text(
            "SELECT document_id, content FROM document_chunks "
            "GROUP BY document_id, content HAVING count(*) > 1"
        )
    ).all()
    assert db.scalar(select(func.count()).select_from(DocumentChunk)) > 38


def test_retrieval_filter_top_k_and_sources(db):
    retriever = RAGRetriever()
    code = "Tomato___Tomato_mosaic_virus"
    results = retriever.retrieve(
        db, "Triệu chứng virus khảm cà chua", top_k=3, disease_code=code
    )
    assert 1 <= len(results) <= 3
    assert all(r["disease_code"] == code and r["source_ids"] for r in results)
    assert all(-1.001 <= r["score"] <= 1.001 for r in results)
    assert build_citations(results)
    assert len(retriever.retrieve(db, "bệnh cây", top_k=1)) == 1
    assert retriever.retrieve(db, "bệnh cây", disease_code="unknown") == []
    policy = lookup_structured_knowledge(db, code)
    assert policy["rules"] == []


def test_sample_query_relevance(db):
    results = RAGRetriever().retrieve(
        db, "Bệnh ghẻ táo Apple scab triệu chứng", top_k=3
    )
    assert "Apple___Apple_scab" in {r["disease_code"] for r in results}


def test_retrieve_endpoint_contract(db):
    app = FastAPI()
    app.include_router(router, prefix="/api/v1")
    app.dependency_overrides[get_db] = lambda: db
    client = TestClient(app)
    url = "/api/v1/disease/knowledge/retrieve"
    payload = {
        "query": "Triệu chứng",
        "disease_code": "Tomato___Late_blight",
        "top_k": 2,
    }
    response = client.post(url, json=payload)
    assert response.status_code == 200
    rows = response.json()
    assert 1 <= len(rows) <= 2
    assert all(
        {"content", "disease_code", "source_ids", "score"} <= r.keys() for r in rows
    )
    assert all(r["disease_code"] == payload["disease_code"] for r in rows)
    assert client.post(url, json={**payload, "top_k": 0}).status_code == 422
    assert client.post(url, json={**payload, "query": " "}).status_code == 422


def test_ingestion_failure_preserves_previous_corpus(db, monkeypatch):
    from app.rag import embedding_service
    from app.rag.ingestion_service import ingest_disease_knowledge

    before = set(db.scalars(select(Document.id)))

    class BrokenEmbeddings:
        def embed_texts(self, texts):
            # The wrong dimension fails after replacement has started in the DB.
            return [[0.0, 1.0] for _ in texts]

    monkeypatch.setattr(embedding_service, "EmbeddingService", BrokenEmbeddings)
    with pytest.raises(Exception, match="384"):
        ingest_disease_knowledge(db)
    assert set(db.scalars(select(Document.id))) == before


@pytest.mark.skipif(
    os.getenv("RUN_REAL_CV_TESTS") != "1", reason="Loads real model weights."
)
def test_real_model_startup_prediction_and_persistence(db, report):
    from pathlib import Path

    from app.main import create_app

    data = (Path(__file__).parents[1] / "data/images/tomato_leaf.jpg").read_bytes()
    app = create_app()
    app.dependency_overrides[get_db] = lambda: db
    with TestClient(app) as client:
        assert client.get("/api/v1/disease/health").json()["model_loaded"] is True
        response = client.post(
            f"/api/v1/disease/reports/{report.id}/diagnose",
            files={"image": ("leaf.jpg", data, "image/jpeg")},
        )
        assert response.status_code == 200, response.text
        result = response.json()
        assert result["prediction"]["disease_code"] in {
            row["disease_code"] for row in class_contract()["classes"]
        }
        assert db.get(Diagnosis, uuid.UUID(result["diagnosis_id"])) is not None
    assert app.state.plant_model is None
