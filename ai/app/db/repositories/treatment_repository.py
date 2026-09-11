import uuid
from datetime import datetime, timedelta, timezone

from sqlalchemy import func, select
from sqlalchemy.orm import Session, joinedload

from ..models.treatment_application import TreatmentApplication
from ..models.treatment_rule import TreatmentRule


def create_treatment_application(
    db: Session,
    crop_season_id: uuid.UUID,
    pesticide_id: uuid.UUID,
    treatment_rule_id: uuid.UUID | None,
    dose: float,
    dose_unit: str,
    phi_days: int,
    applied_at: datetime,
) -> TreatmentApplication:
    """Record one pesticide application that actually happened."""

    if applied_at.tzinfo is None:
        applied_at = applied_at.replace(tzinfo=timezone.utc)

    harvest_locked_until = applied_at + timedelta(days=phi_days)

    treatment = TreatmentApplication(
        crop_season_id=crop_season_id,
        pesticide_id=pesticide_id,
        treatment_rule_id=treatment_rule_id,
        dose=dose,
        dose_unit=dose_unit,
        phi_days=phi_days,
        applied_at=applied_at,
        harvest_locked_until=harvest_locked_until,
        status="APPLIED",
    )

    db.add(treatment)
    db.commit()
    db.refresh(treatment)
    return treatment


def create_treatment_from_rule(
    db: Session,
    crop_season_id: uuid.UUID,
    rule: TreatmentRule,
    actual_dose: float,
    applied_at: datetime,
) -> TreatmentApplication:
    """Validate the approved actual dose, then persist its snapshot."""

    if not rule.dose_min <= actual_dose <= rule.dose_max:
        raise ValueError(
            "actual_dose must be between "
            f"{rule.dose_min} and {rule.dose_max} {rule.dose_unit}"
        )

    return create_treatment_application(
        db=db,
        crop_season_id=crop_season_id,
        pesticide_id=rule.pesticide_id,
        treatment_rule_id=rule.id,
        dose=actual_dose,
        dose_unit=rule.dose_unit,
        phi_days=rule.phi_days,
        applied_at=applied_at,
    )


def get_harvest_lock_until(
    db: Session,
    crop_season_id: uuid.UUID,
) -> datetime | None:
    """Return the latest effective PHI boundary across all applied treatments."""

    return db.scalar(
        select(func.max(TreatmentApplication.harvest_locked_until)).where(
            TreatmentApplication.crop_season_id == crop_season_id,
            TreatmentApplication.status == "APPLIED",
        )
    )


def get_latest_treatment(
    db: Session,
    crop_season_id: uuid.UUID,
) -> TreatmentApplication | None:
    """Return the latest applied treatment for a crop season."""

    stmt = (
        select(TreatmentApplication)
        .where(
            TreatmentApplication.crop_season_id == crop_season_id,
            TreatmentApplication.status == "APPLIED",
        )
        .options(
            joinedload(TreatmentApplication.pesticide),
            joinedload(TreatmentApplication.treatment_rule),
        )
        .order_by(TreatmentApplication.applied_at.desc())
        .limit(1)
    )
    return db.scalar(stmt)


def get_treatment_history(
    db: Session,
    crop_season_id: uuid.UUID,
) -> list[TreatmentApplication]:
    """Return all applied treatments for a crop season."""

    stmt = (
        select(TreatmentApplication)
        .where(TreatmentApplication.crop_season_id == crop_season_id)
        .options(
            joinedload(TreatmentApplication.pesticide),
            joinedload(TreatmentApplication.treatment_rule),
        )
        .order_by(TreatmentApplication.applied_at.desc())
    )
    return list(db.scalars(stmt).all())


def is_harvest_blocked(
    db: Session,
    crop_season_id: uuid.UUID,
    at_time: datetime | None = None,
) -> bool:
    """Return whether the latest treatment still locks harvest."""

    if at_time is None:
        at_time = datetime.now(timezone.utc)

    locked_until = get_harvest_lock_until(db=db, crop_season_id=crop_season_id)
    if locked_until is None:
        return False
    return at_time < locked_until
