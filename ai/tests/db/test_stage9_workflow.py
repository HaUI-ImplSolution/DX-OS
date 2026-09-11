import os
import uuid
from datetime import date, datetime, timedelta, timezone

import pytest
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.db.models import (
    CropSeason,
    Disease,
    DiseaseReport,
    Parcel,
    TreatmentApplication,
    TreatmentRule,
    WorkflowEvent,
)
from app.db.repositories.treatment_repository import (
    get_harvest_lock_until,
    is_harvest_blocked,
)
from app.db.session import engine
from app.services.workflow_service import (
    WorkflowConflictError,
    apply_workflow_case,
    approve_workflow_case,
    create_workflow_case,
    reject_workflow_case,
    serialize_case,
)

pytestmark = pytest.mark.skipif(
    os.getenv("RUN_STAGE910_DB_TESTS") != "1",
    reason="Requires migrated and seeded PostgreSQL.",
)


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
def season_report(db):
    parcel = Parcel(
        code=f"stage9-{uuid.uuid4().hex[:10]}",
        name="Stage 9",
        area_ha=1.0,
    )
    db.add(parcel)
    db.flush()
    season = CropSeason(
        parcel_id=parcel.id,
        season_name="Demo",
        rice_variety="Demo crop",
        start_date=date(2026, 9, 11),
    )
    db.add(season)
    db.flush()
    report = DiseaseReport(
        crop_season_id=season.id, image_url="test://stage9", status="ANALYZED"
    )
    db.add(report)
    db.commit()
    return season, report


def treatment_case(db, season_report, *, low_confidence=False):
    season, report = season_report
    disease = db.scalar(select(Disease).where(Disease.code == "Tomato___Late_blight"))
    rule = db.scalar(
        select(TreatmentRule).where(TreatmentRule.disease_id == disease.id)
    )
    recommendation = {
        "path": "treatment",
        "disease_code": disease.code,
        "actions": [
            {
                "rule_id": str(rule.id),
                "pesticide": rule.pesticide.name,
                "dose_min": rule.dose_min,
                "dose_max": rule.dose_max,
                "dose_unit": rule.dose_unit,
                "phi_days": rule.phi_days,
            }
        ],
    }
    case = create_workflow_case(
        db,
        request_id=f"req-{uuid.uuid4()}",
        crop_season_id=season.id,
        disease_report_id=report.id,
        disease_code=disease.code,
        structured_path="treatment",
        recommendation=recommendation,
        rag_source_ids=["S05"],
        low_confidence=low_confidence,
        requested_by="farmer-demo",
    )
    return case, rule


def test_approved_is_not_applied_then_applied_is_idempotent(db, season_report):
    case, rule = treatment_case(db, season_report)
    planned = (rule.dose_min + rule.dose_max) / 2
    approved = approve_workflow_case(
        db,
        case.id,
        actor_id="engineer-demo",
        selected_rule_id=rule.id,
        approved_dose=planned,
    )
    assert approved.approval_status == "APPROVED"
    assert approved.application_status == "WAITING_APPLIED"
    assert approved.application_id is None
    assert get_harvest_lock_until(db, approved.crop_season_id) is None

    applied_at = datetime(2026, 9, 11, 8, tzinfo=timezone.utc)
    applied = apply_workflow_case(
        db,
        case.id,
        actor_id="farmer-demo",
        actual_dose=planned,
        applied_at=applied_at,
    )
    payload = serialize_case(applied)
    assert payload["application_status"] == "APPLIED"
    assert payload["application"]["actual_dose"] == planned
    assert payload["application"]["pesticide_name"] == rule.pesticide.name
    assert payload["application"]["rule_version"] == rule.version
    assert datetime.fromisoformat(
        payload["application"]["harvest_locked_until"]
    ) == applied_at + timedelta(days=rule.phi_days)

    retried = apply_workflow_case(
        db,
        case.id,
        actor_id="farmer-demo",
        actual_dose=planned,
        applied_at=applied_at,
    )
    assert retried.application_id == applied.application_id
    with pytest.raises(WorkflowConflictError, match="different payload"):
        apply_workflow_case(
            db,
            case.id,
            actor_id="farmer-demo",
            actual_dose=planned + 0.01,
            applied_at=applied_at,
        )
    assert (
        db.scalar(
            select(func.count())
            .select_from(TreatmentApplication)
            .where(TreatmentApplication.id == applied.application_id)
        )
        == 1
    )
    assert (
        db.scalar(
            select(func.count())
            .select_from(WorkflowEvent)
            .where(
                WorkflowEvent.workflow_case_id == case.id,
                WorkflowEvent.event_type == "APPLIED",
            )
        )
        == 1
    )


def test_reject_persists_reason_and_cannot_apply(db, season_report):
    case, _ = treatment_case(db, season_report)
    rejected = reject_workflow_case(
        db, case.id, actor_id="engineer-demo", reason="Image is unclear"
    )
    assert rejected.approval_status == "REJECTED"
    assert rejected.approval_reason == "Image is unclear"
    assert rejected.application_id is None
    assert rejected.events[-1].event_type == "REJECTED"
    with pytest.raises(WorkflowConflictError):
        apply_workflow_case(
            db,
            case.id,
            actor_id="farmer-demo",
            actual_dose=1,
            applied_at=datetime.now(timezone.utc),
        )


def test_out_of_range_dose_rolls_back_without_phi(db, season_report):
    case, rule = treatment_case(db, season_report)
    approve_workflow_case(
        db,
        case.id,
        actor_id="engineer-demo",
        selected_rule_id=rule.id,
        approved_dose=rule.dose_min,
    )
    with pytest.raises(ValueError, match="actual_dose"):
        apply_workflow_case(
            db,
            case.id,
            actor_id="farmer-demo",
            actual_dose=rule.dose_max + 1,
            applied_at=datetime.now(timezone.utc),
        )
    assert get_harvest_lock_until(db, case.crop_season_id) is None


def test_low_confidence_requires_explicit_confirmation(db, season_report):
    case, rule = treatment_case(db, season_report, low_confidence=True)
    with pytest.raises(ValueError, match="explicit confirmation"):
        approve_workflow_case(
            db,
            case.id,
            actor_id="engineer-demo",
            selected_rule_id=rule.id,
            approved_dose=rule.dose_min,
        )
    approved = approve_workflow_case(
        db,
        case.id,
        actor_id="engineer-demo",
        selected_rule_id=rule.id,
        approved_dose=rule.dose_min,
        confirmed_low_confidence=True,
    )
    assert approved.approval_status == "APPROVED"


def test_policy_case_never_creates_chemical_application(db, season_report):
    season, report = season_report
    case = create_workflow_case(
        db,
        request_id=f"policy-{uuid.uuid4()}",
        crop_season_id=season.id,
        disease_report_id=report.id,
        disease_code="Tomato___Tomato_mosaic_virus",
        structured_path="policy",
        recommendation={
            "path": "policy",
            "actions": [],
            "policy": {"management_status": "NO_CHEMICAL_CONTROL"},
        },
        rag_source_ids=["S01"],
        low_confidence=False,
    )
    approved = approve_workflow_case(db, case.id, actor_id="engineer-demo")
    assert approved.application_status == "NOT_APPLICABLE"
    with pytest.raises(WorkflowConflictError):
        apply_workflow_case(
            db,
            case.id,
            actor_id="farmer-demo",
            actual_dose=1,
            applied_at=datetime.now(timezone.utc),
        )


def test_overlapping_applications_use_latest_lock_not_latest_application(
    db, season_report
):
    season, _ = season_report
    disease = db.scalar(select(Disease).where(Disease.code == "Tomato___Late_blight"))
    rule = db.scalar(
        select(TreatmentRule).where(TreatmentRule.disease_id == disease.id)
    )
    first = TreatmentApplication(
        crop_season_id=season.id,
        pesticide_id=rule.pesticide_id,
        treatment_rule_id=rule.id,
        dose=rule.dose_min,
        dose_unit=rule.dose_unit,
        phi_days=10,
        applied_at=datetime(2026, 9, 1, tzinfo=timezone.utc),
        harvest_locked_until=datetime(2026, 9, 11, tzinfo=timezone.utc),
        status="APPLIED",
    )
    later_shorter = TreatmentApplication(
        crop_season_id=season.id,
        pesticide_id=rule.pesticide_id,
        treatment_rule_id=rule.id,
        dose=rule.dose_min,
        dose_unit=rule.dose_unit,
        phi_days=1,
        applied_at=datetime(2026, 9, 5, tzinfo=timezone.utc),
        harvest_locked_until=datetime(2026, 9, 6, tzinfo=timezone.utc),
        status="APPLIED",
    )
    db.add_all([first, later_shorter])
    db.commit()
    assert get_harvest_lock_until(db, season.id) == datetime(
        2026, 9, 11, tzinfo=timezone.utc
    )
    assert is_harvest_blocked(db, season.id, datetime(2026, 9, 7, tzinfo=timezone.utc))
