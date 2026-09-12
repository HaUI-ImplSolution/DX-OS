from sqlalchemy.orm import Session

from src.db.repositories.diagnosis_repository import (
    get_latest_diagnosis,
)
from src.db.repositories.parcel_repository import (
    get_current_crop_season,
    get_parcel_by_code,
)
from src.db.repositories.treatment_repository import (
    get_harvest_lock_until,
    get_latest_treatment,
    is_harvest_blocked,
)


def get_field_context(
    db: Session,
    parcel_code: str | None = None,
    crop_season_id=None,
) -> dict:
    """
    Gom toàn bộ operational context của một thửa ruộng
    thành JSON/dict để LangGraph sử dụng.
    """

    if crop_season_id is not None:
        from src.db.models.crop_season import CropSeason

        season = db.get(CropSeason, crop_season_id)
        if season is None:
            raise ValueError(f"Crop season '{crop_season_id}' does not exist")
        parcel = season.parcel
        if parcel_code and parcel.code != parcel_code:
            raise ValueError("Crop season does not belong to the requested parcel")
    else:
        if not parcel_code:
            raise ValueError("parcel_code or crop_season_id is required")
        parcel = get_parcel_by_code(db=db, code=parcel_code)
        season = None

    if parcel is None:
        raise ValueError(f"Parcel '{parcel_code}' does not exist")

    if season is None:
        season = get_current_crop_season(db=db, parcel_id=parcel.id)

    context = {
        "parcel": {
            "id": str(parcel.id),
            "code": parcel.code,
            "name": parcel.name,
            "area_ha": parcel.area_ha,
            "location": parcel.location,
            "status": parcel.status,
        },
        "current_season": None,
        "latest_diagnosis": None,
        "latest_treatment": None,
        "harvest": {
            "is_blocked": False,
            "locked_until": None,
        },
    }

    if season is None:
        return context

    context["current_season"] = {
        "id": str(season.id),
        "season_name": season.season_name,
        "rice_variety": season.rice_variety,
        "start_date": season.start_date.isoformat(),
        "expected_harvest_date": (
            season.expected_harvest_date.isoformat()
            if season.expected_harvest_date
            else None
        ),
        "status": season.status,
    }

    diagnosis = get_latest_diagnosis(
        db=db,
        crop_season_id=season.id,
    )

    if diagnosis is not None:
        context["latest_diagnosis"] = {
            "disease_id": str(diagnosis.disease_id),
            "disease_code": diagnosis.disease.code,
            "disease_name": diagnosis.disease.name,
            "confidence": diagnosis.confidence,
            "model_version": diagnosis.model_version,
            "diagnosed_at": diagnosis.diagnosed_at.isoformat(),
        }

    treatment = get_latest_treatment(
        db=db,
        crop_season_id=season.id,
    )

    if treatment is not None:
        context["latest_treatment"] = {
            "id": str(treatment.id),
            "pesticide_id": str(treatment.pesticide_id),
            "pesticide_name": treatment.pesticide.name,
            "active_ingredient": (treatment.pesticide.active_ingredient),
            "dose": treatment.dose,
            "dose_unit": treatment.dose_unit,
            "phi_days": treatment.phi_days,
            "applied_at": treatment.applied_at.isoformat(),
            "harvest_locked_until": (treatment.harvest_locked_until.isoformat()),
            "status": treatment.status,
        }

        locked_until = get_harvest_lock_until(db=db, crop_season_id=season.id)
        context["harvest"] = {
            "is_blocked": is_harvest_blocked(
                db=db,
                crop_season_id=season.id,
            ),
            "locked_until": locked_until.isoformat() if locked_until else None,
        }

    return context
