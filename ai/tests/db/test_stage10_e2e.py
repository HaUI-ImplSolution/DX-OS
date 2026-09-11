import importlib
import os
import uuid
from datetime import date, datetime, timezone

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.api.router import router
from app.db.models import (
    CropSeason,
    Diagnosis,
    DiseaseReport,
    Parcel,
    TreatmentApplication,
    WorkflowCase,
)
from app.db.session import engine, get_db
from tests.cv.test_stage6_contract import Model, image_bytes

pytestmark = pytest.mark.skipif(
    os.getenv("RUN_STAGE910_DB_TESTS") != "1",
    reason="Requires migrated, seeded and ingested PostgreSQL.",
)


class Writer:
    def write(self, evidence):
        return evidence, "test_grounded"


@pytest.fixture
def db():
    with engine.connect() as connection:
        outer = connection.begin()
        with Session(
            bind=connection, join_transaction_mode="create_savepoint"
        ) as session:
            yield session
        outer.rollback()


@pytest.fixture
def parcel_season(db):
    parcel = Parcel(
        code=f"e2e-{uuid.uuid4().hex[:10]}",
        name="E2E demo",
        area_ha=1,
    )
    db.add(parcel)
    db.flush()
    season = CropSeason(
        parcel_id=parcel.id,
        season_name="E2E",
        rice_variety="Demo crop",
        start_date=date(2026, 9, 11),
    )
    db.add(season)
    db.commit()
    return parcel, season


@pytest.fixture
def client(db, monkeypatch):
    router_module = importlib.import_module("app.api.router")
    monkeypatch.setattr(router_module, "GroundedRecommendationWriter", Writer)
    app = FastAPI()
    app.include_router(router, prefix="/api/v1")
    app.state.plant_model = Model(30)
    app.dependency_overrides[get_db] = lambda: db
    return TestClient(app), app


def run_workflow(client, parcel_code, request_id):
    return client.post(
        "/api/v1/disease/workflow/run",
        data={
            "request_id": request_id,
            "parcel_code": parcel_code,
            "requested_by": "farmer-demo",
        },
        files={"image": ("leaf.png", image_bytes(), "image/png")},
    )


def test_treatment_e2e_api_to_phi_and_history(db, client, parcel_season):
    http, _ = client
    parcel, season = parcel_season
    response = run_workflow(http, parcel.code, f"e2e-{uuid.uuid4()}")
    assert response.status_code == 200, response.text
    pending = response.json()
    assert pending["approval_status"] == "PENDING"
    assert pending["recommendation"]["path"] == "treatment"
    case_id = pending["case_id"]
    rule = pending["treatment_rule"]["rules"][0]
    planned = (rule["dose_min"] + rule["dose_max"]) / 2

    approved = http.post(
        f"/api/v1/disease/workflow/{case_id}/approve",
        json={
            "actor_id": "engineer-demo",
            "selected_rule_id": rule["id"],
            "approved_dose": planned,
        },
    )
    assert approved.status_code == 200, approved.text
    assert approved.json()["application_status"] == "WAITING_APPLIED"
    assert approved.json()["application"] is None

    applied_at = "2026-09-11T08:00:00Z"
    applied = http.post(
        f"/api/v1/disease/workflow/{case_id}/applied",
        json={
            "actor_id": "farmer-demo",
            "actual_dose": planned,
            "applied_at": applied_at,
        },
    )
    assert applied.status_code == 200, applied.text
    body = applied.json()
    assert body["application_status"] == "APPLIED"
    assert body["application"]["actual_dose"] == planned
    assert [e["event_type"] for e in body["events"]] == [
        "RECOMMENDATION_PENDING",
        "APPROVED",
        "APPLIED",
    ]

    retry = http.post(
        f"/api/v1/disease/workflow/{case_id}/applied",
        json={
            "actor_id": "farmer-demo",
            "actual_dose": planned,
            "applied_at": applied_at,
        },
    )
    assert retry.status_code == 200
    assert retry.json()["application"]["id"] == body["application"]["id"]
    assert db.scalar(select(func.count()).select_from(Diagnosis)) >= 1
    assert (
        db.scalar(
            select(func.count())
            .select_from(TreatmentApplication)
            .where(TreatmentApplication.crop_season_id == season.id)
        )
        == 1
    )


def test_policy_and_reject_api_paths_are_safe(db, client, parcel_season):
    http, app = client
    parcel, _ = parcel_season
    app.state.plant_model = Model(36)
    response = run_workflow(http, parcel.code, f"policy-{uuid.uuid4()}")
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["recommendation"]["path"] == "policy"
    assert body["recommendation"]["actions"] == []
    assert body["treatment_rule"]["rules"] == []

    rejected = http.post(
        f"/api/v1/disease/workflow/{body['case_id']}/reject",
        json={"actor_id": "engineer-demo", "reason": "Need another image"},
    )
    assert rejected.status_code == 200
    rejected_body = rejected.json()
    assert rejected_body["approval_status"] == "REJECTED"
    assert rejected_body["approval_reason"] == "Need another image"
    assert rejected_body["application"] is None
    applied = http.post(
        f"/api/v1/disease/workflow/{body['case_id']}/applied",
        json={
            "actor_id": "farmer-demo",
            "actual_dose": 1,
            "applied_at": datetime.now(timezone.utc).isoformat(),
        },
    )
    assert applied.status_code == 409


def test_invalid_dose_and_cv_unavailable_fail_safely(db, client, parcel_season):
    http, app = client
    parcel, _ = parcel_season
    response = run_workflow(http, parcel.code, f"dose-{uuid.uuid4()}")
    body = response.json()
    rule = body["treatment_rule"]["rules"][0]
    approved = http.post(
        f"/api/v1/disease/workflow/{body['case_id']}/approve",
        json={
            "actor_id": "engineer-demo",
            "selected_rule_id": rule["id"],
            "approved_dose": rule["dose_min"],
        },
    )
    assert approved.status_code == 200
    invalid = http.post(
        f"/api/v1/disease/workflow/{body['case_id']}/applied",
        json={
            "actor_id": "farmer-demo",
            "actual_dose": rule["dose_max"] + 1,
            "applied_at": "2026-09-11T08:00:00Z",
        },
    )
    assert invalid.status_code == 422
    assert invalid.json()["detail"].startswith("actual_dose")

    before = db.scalar(select(func.count()).select_from(DiseaseReport))
    app.state.plant_model = None
    unavailable = run_workflow(http, parcel.code, f"down-{uuid.uuid4()}")
    assert unavailable.status_code == 503
    assert db.scalar(select(func.count()).select_from(DiseaseReport)) == before


def test_request_id_retry_returns_same_case(db, client, parcel_season):
    http, _ = client
    parcel, _ = parcel_season
    request_id = f"retry-{uuid.uuid4()}"
    first = run_workflow(http, parcel.code, request_id)
    second = run_workflow(http, parcel.code, request_id)
    assert first.status_code == second.status_code == 200
    assert first.json()["case_id"] == second.json()["case_id"]
    assert (
        db.scalar(
            select(func.count())
            .select_from(WorkflowCase)
            .where(WorkflowCase.request_id == request_id)
        )
        == 1
    )


def test_graph_failure_marks_report_failed(db, client, parcel_season, monkeypatch):
    router_module = importlib.import_module("app.api.router")

    def fail_graph(_state):
        raise RuntimeError("simulated internal failure")

    monkeypatch.setattr(router_module.farm_graph, "invoke", fail_graph)
    http, _ = client
    parcel, _ = parcel_season
    request_id = f"graph-fail-{uuid.uuid4()}"

    response = run_workflow(http, parcel.code, request_id)

    assert response.status_code == 500
    assert response.json() == {"detail": "Workflow execution failed"}
    report = db.scalar(
        select(DiseaseReport).where(
            DiseaseReport.image_url == f"workflow://{request_id}"
        )
    )
    assert report is not None
    assert report.status == "FAILED"
