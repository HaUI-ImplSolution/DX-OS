import uuid
from datetime import datetime, timedelta, timezone

from sqlalchemy import select
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
    """
    Ghi nhận một lần phun thuốc đã thực sự xảy ra ngoài ruộng.
    """

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
    applied_at: datetime,
) -> TreatmentApplication:
    """
    Convenience function:
    lấy thông tin từ TreatmentRule và snapshot vào TreatmentApplication.
    """

    return create_treatment_application(
        db=db,
        crop_season_id=crop_season_id,
        pesticide_id=rule.pesticide_id,
        treatment_rule_id=rule.id,
        dose=rule.dose,
        dose_unit=rule.dose_unit,
        phi_days=rule.phi_days,
        applied_at=applied_at,
    )


def get_latest_treatment(
    db: Session,
    crop_season_id: uuid.UUID,
) -> TreatmentApplication | None:
    """
    Lấy lần phun gần nhất của vụ mùa.
    """

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
    """
    Lấy toàn bộ lịch sử phun của một vụ mùa.
    """

    stmt = (
        select(TreatmentApplication)
        .where(
            TreatmentApplication.crop_season_id == crop_season_id,
        )
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
    """
    Kiểm tra vụ hiện tại có đang trong thời gian cách ly hay không.
    """

    if at_time is None:
        at_time = datetime.now(timezone.utc)

    latest = get_latest_treatment(
        db=db,
        crop_season_id=crop_season_id,
    )

    if latest is None:
        return False

    return at_time < latest.harvest_locked_until