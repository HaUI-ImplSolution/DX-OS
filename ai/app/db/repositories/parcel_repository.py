from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models.parcel import Parcel
from ..models.crop_season import CropSeason


def get_parcel_by_code(
    db: Session,
    code: str,
) -> Parcel | None:
    stmt = (
        select(Parcel)
        .where(Parcel.code == code)
    )

    return db.scalar(stmt)


def get_parcel_by_id(
    db: Session,
    parcel_id,
) -> Parcel | None:
    stmt = (
        select(Parcel)
        .where(Parcel.id == parcel_id)
    )

    return db.scalar(stmt)


def get_current_crop_season(
    db: Session,
    parcel_id,
) -> CropSeason | None:
    stmt = (
        select(CropSeason)
        .where(
            CropSeason.parcel_id == parcel_id,
            CropSeason.status == "ACTIVE",
        )
        .order_by(CropSeason.start_date.desc())
        .limit(1)
    )

    return db.scalar(stmt)


def get_default_crop_season(db: Session) -> CropSeason | None:
    stmt = (
        select(CropSeason)
        .where(CropSeason.status == "ACTIVE")
        .order_by(CropSeason.start_date.desc())
        .limit(1)
    )
    season = db.scalar(stmt)
    if season is None:
        stmt_any = select(CropSeason).limit(1)
        season = db.scalar(stmt_any)
    return season

