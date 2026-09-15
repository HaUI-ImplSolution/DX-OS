"""Idempotent Stage 10 parcel/crop-season fixtures."""

from datetime import date

from sqlalchemy import select

from app.db.models import CropSeason, Parcel
from app.db.session import SessionLocal

PARCEL_CODE = "DEMO-PARCEL-001"
SEASON_NAME = "DX-Farm MVP 2026"


def seed_demo_fixtures() -> dict[str, str]:
    with SessionLocal() as db:
        try:
            parcel = db.scalar(select(Parcel).where(Parcel.code == PARCEL_CODE))
            if parcel is None:
                parcel = Parcel(
                    code=PARCEL_CODE,
                    name="Thửa demo DX-Farm",
                    area_ha=1.0,
                    location="Local MVP fixture",
                    status="ACTIVE",
                )
                db.add(parcel)
                db.flush()
            season = db.scalar(
                select(CropSeason).where(
                    CropSeason.parcel_id == parcel.id,
                    CropSeason.season_name == SEASON_NAME,
                )
            )
            if season is None:
                season = CropSeason(
                    parcel_id=parcel.id,
                    season_name=SEASON_NAME,
                    rice_variety="Demo crop",
                    start_date=date(2026, 9, 1),
                    expected_harvest_date=date(2026, 12, 1),
                    status="ACTIVE",
                )
                db.add(season)
            db.commit()
            db.refresh(season)
            return {
                "parcel_code": parcel.code,
                "crop_season_id": str(season.id),
            }
        except Exception:
            db.rollback()
            raise


def main() -> None:
    print(seed_demo_fixtures())


if __name__ == "__main__":
    main()
