"""Durable human-in-the-loop transitions for Stage 8-10."""

import math
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any

from sqlalchemy import func, select
from sqlalchemy.orm import Session, selectinload

from src.db.models.treatment_application import TreatmentApplication
from src.db.models.treatment_rule import TreatmentRule
from src.db.models.workflow_case import WorkflowCase, WorkflowEvent


class WorkflowConflictError(RuntimeError):
    pass


def _utc(value: datetime | None = None) -> datetime:
    value = value or datetime.now(timezone.utc)
    return value.replace(tzinfo=timezone.utc) if value.tzinfo is None else value


def _case_query(case_id: uuid.UUID):
    return (
        select(WorkflowCase)
        .where(WorkflowCase.id == case_id)
        .options(
            selectinload(WorkflowCase.events),
            selectinload(WorkflowCase.application),
        )
    )


def get_workflow_case(db: Session, case_id: uuid.UUID) -> WorkflowCase:
    case = db.scalar(_case_query(case_id))
    if case is None:
        raise LookupError("Workflow case not found")
    return case


def get_case_by_request_id(db: Session, request_id: str) -> WorkflowCase | None:
    return db.scalar(
        select(WorkflowCase)
        .where(WorkflowCase.request_id == request_id)
        .options(
            selectinload(WorkflowCase.events),
            selectinload(WorkflowCase.application),
        )
    )


def _append_event(
    db: Session,
    case: WorkflowCase,
    event_type: str,
    actor_id: str | None,
    details: dict[str, Any] | None = None,
) -> None:
    next_sequence = (
        db.scalar(
            select(func.coalesce(func.max(WorkflowEvent.sequence), 0)).where(
                WorkflowEvent.workflow_case_id == case.id
            )
        )
        + 1
    )
    db.add(
        WorkflowEvent(
            workflow_case_id=case.id,
            sequence=next_sequence,
            event_type=event_type,
            actor_id=actor_id,
            details=details or {},
        )
    )


def serialize_case(case: WorkflowCase) -> dict[str, Any]:
    application = case.application
    return {
        "case_id": str(case.id),
        "request_id": case.request_id,
        "crop_season_id": str(case.crop_season_id),
        "disease_report_id": str(case.disease_report_id),
        "disease_code": case.disease_code,
        "structured_path": case.structured_path,
        "recommendation": case.recommendation,
        "rag_source_ids": case.rag_source_ids,
        "low_confidence": case.low_confidence,
        "approval_status": case.approval_status,
        "application_status": case.application_status,
        "approval_reason": case.approval_reason,
        "selected_rule_id": (
            str(case.selected_rule_id) if case.selected_rule_id else None
        ),
        "approved_dose": case.approved_dose,
        "application": None
        if application is None
        else {
            "id": str(application.id),
            "pesticide_id": str(application.pesticide_id),
            "pesticide_name": application.pesticide_name,
            "active_ingredient": application.active_ingredient,
            "actual_dose": application.dose,
            "dose_unit": application.dose_unit,
            "phi_days": application.phi_days,
            "rule_version": application.rule_version,
            "rule_source": application.rule_source,
            "applied_at": application.applied_at.isoformat(),
            "harvest_locked_until": application.harvest_locked_until.isoformat(),
            "status": application.status,
        },
        "events": [
            {
                "sequence": event.sequence,
                "event_type": event.event_type,
                "actor_id": event.actor_id,
                "details": event.details,
                "created_at": event.created_at.isoformat(),
            }
            for event in case.events
        ],
        "created_at": case.created_at.isoformat(),
        "updated_at": case.updated_at.isoformat(),
    }


def create_workflow_case(
    db: Session,
    *,
    request_id: str,
    crop_season_id: uuid.UUID,
    disease_report_id: uuid.UUID,
    disease_code: str,
    structured_path: str,
    recommendation: dict[str, Any],
    rag_source_ids: list[str],
    low_confidence: bool,
    requested_by: str | None = None,
) -> WorkflowCase:
    existing = get_case_by_request_id(db, request_id)
    if existing is not None:
        return existing
    try:
        case = WorkflowCase(
            request_id=request_id,
            crop_season_id=crop_season_id,
            disease_report_id=disease_report_id,
            disease_code=disease_code,
            structured_path=structured_path,
            recommendation=recommendation,
            rag_source_ids=list(dict.fromkeys(rag_source_ids)),
            low_confidence=low_confidence,
            approval_status="PENDING",
            application_status="NOT_READY",
            requested_by=requested_by,
        )
        db.add(case)
        db.flush()
        _append_event(
            db,
            case,
            "RECOMMENDATION_PENDING",
            requested_by,
            {
                "disease_code": disease_code,
                "structured_path": structured_path,
                "low_confidence": low_confidence,
            },
        )
        db.commit()
        return get_workflow_case(db, case.id)
    except Exception:
        db.rollback()
        raise


def _locked_case(db: Session, case_id: uuid.UUID) -> WorkflowCase:
    case = db.scalar(
        select(WorkflowCase).where(WorkflowCase.id == case_id).with_for_update()
    )
    if case is None:
        raise LookupError("Workflow case not found")
    return case


def approve_workflow_case(
    db: Session,
    case_id: uuid.UUID,
    *,
    actor_id: str,
    selected_rule_id: uuid.UUID | None = None,
    approved_dose: float | None = None,
    confirmed_low_confidence: bool = False,
) -> WorkflowCase:
    try:
        case = _locked_case(db, case_id)
        if case.approval_status == "REJECTED":
            raise WorkflowConflictError("Rejected workflow cannot be approved")
        if case.approval_status == "APPROVED":
            if (
                case.selected_rule_id == selected_rule_id
                and case.approved_dose == approved_dose
            ):
                db.rollback()
                return get_workflow_case(db, case_id)
            raise WorkflowConflictError("Workflow is already approved")
        if case.low_confidence and not confirmed_low_confidence:
            raise ValueError("Low-confidence diagnosis requires explicit confirmation")

        if case.structured_path == "treatment":
            if selected_rule_id is None or approved_dose is None:
                raise ValueError(
                    "selected_rule_id and approved_dose are required for treatment"
                )
            if not math.isfinite(approved_dose):
                raise ValueError("approved_dose must be finite")
            rule = db.get(TreatmentRule, selected_rule_id)
            allowed_ids = {
                action.get("rule_id")
                for action in case.recommendation.get("actions", [])
            }
            if (
                rule is None
                or str(rule.id) not in allowed_ids
                or rule.disease.code != case.disease_code
            ):
                raise ValueError("Selected rule does not belong to this diagnosis")
            if not rule.dose_min <= approved_dose <= rule.dose_max:
                raise ValueError(
                    "approved_dose must be between "
                    f"{rule.dose_min} and {rule.dose_max} {rule.dose_unit}"
                )
            case.selected_rule_id = rule.id
            case.approved_dose = approved_dose
            case.application_status = "WAITING_APPLIED"
        else:
            if selected_rule_id is not None or approved_dose is not None:
                raise ValueError(
                    "Policy/review path cannot select a chemical rule or dose"
                )
            case.application_status = "NOT_APPLICABLE"

        case.approval_status = "APPROVED"
        case.decided_by = actor_id
        case.decided_at = _utc()
        _append_event(
            db,
            case,
            "APPROVED",
            actor_id,
            {
                "selected_rule_id": (
                    str(case.selected_rule_id) if case.selected_rule_id else None
                ),
                "approved_dose": case.approved_dose,
                "application_status": case.application_status,
                "confirmed_low_confidence": confirmed_low_confidence,
            },
        )
        db.commit()
        return get_workflow_case(db, case_id)
    except Exception:
        db.rollback()
        raise


def reject_workflow_case(
    db: Session,
    case_id: uuid.UUID,
    *,
    actor_id: str,
    reason: str,
) -> WorkflowCase:
    reason = reason.strip()
    if not reason:
        raise ValueError("Rejection reason is required")
    try:
        case = _locked_case(db, case_id)
        if case.approval_status == "REJECTED" and case.approval_reason == reason:
            db.rollback()
            return get_workflow_case(db, case_id)
        if case.approval_status != "PENDING":
            raise WorkflowConflictError("Only a pending workflow can be rejected")
        case.approval_status = "REJECTED"
        case.application_status = "CANCELLED"
        case.approval_reason = reason
        case.decided_by = actor_id
        case.decided_at = _utc()
        _append_event(db, case, "REJECTED", actor_id, {"reason": reason})
        db.commit()
        return get_workflow_case(db, case_id)
    except Exception:
        db.rollback()
        raise


def apply_workflow_case(
    db: Session,
    case_id: uuid.UUID,
    *,
    actor_id: str,
    actual_dose: float,
    applied_at: datetime,
) -> WorkflowCase:
    if not math.isfinite(actual_dose):
        raise ValueError("actual_dose must be finite")
    applied_at = _utc(applied_at)
    try:
        case = _locked_case(db, case_id)
        if case.application_id is not None:
            application = db.get(TreatmentApplication, case.application_id)
            if (
                application is None
                or application.dose != actual_dose
                or _utc(application.applied_at) != applied_at
            ):
                raise WorkflowConflictError(
                    "Workflow is already applied with a different payload"
                )
            db.rollback()
            return get_workflow_case(db, case_id)
        if case.approval_status != "APPROVED":
            raise WorkflowConflictError("Only an approved workflow can be applied")
        if case.structured_path != "treatment" or case.selected_rule_id is None:
            raise WorkflowConflictError(
                "Policy/review workflow cannot create a chemical application"
            )
        rule = db.get(TreatmentRule, case.selected_rule_id)
        if rule is None or rule.disease.code != case.disease_code:
            raise ValueError("Approved treatment rule is no longer valid")
        if not rule.dose_min <= actual_dose <= rule.dose_max:
            raise ValueError(
                "actual_dose must be between "
                f"{rule.dose_min} and {rule.dose_max} {rule.dose_unit}"
            )

        application = TreatmentApplication(
            crop_season_id=case.crop_season_id,
            pesticide_id=rule.pesticide_id,
            treatment_rule_id=rule.id,
            pesticide_name=rule.pesticide.name,
            active_ingredient=rule.pesticide.active_ingredient,
            rule_version=rule.version,
            rule_source=rule.source,
            dose=actual_dose,
            dose_unit=rule.dose_unit,
            phi_days=rule.phi_days,
            applied_at=applied_at,
            harvest_locked_until=applied_at + timedelta(days=rule.phi_days),
            status="APPLIED",
        )
        db.add(application)
        db.flush()
        case.application_id = application.id
        case.application_status = "APPLIED"
        case.applied_at = applied_at
        case.applied_by = actor_id
        _append_event(
            db,
            case,
            "APPLIED",
            actor_id,
            {
                "application_id": str(application.id),
                "actual_dose": actual_dose,
                "dose_unit": rule.dose_unit,
                "phi_days": rule.phi_days,
                "harvest_locked_until": application.harvest_locked_until.isoformat(),
            },
        )
        db.commit()
        return get_workflow_case(db, case_id)
    except Exception:
        db.rollback()
        raise
