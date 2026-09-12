import uuid
from datetime import date

from sqlalchemy import Date, ForeignKey, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from typing import TYPE_CHECKING

from src.db.base import Base

if TYPE_CHECKING:
    from .parcel import Parcel
    from .disease_report import DiseaseReport
    from .treatment_application import TreatmentApplication




class CropSeason(Base):
    __tablename__ = "crop_seasons"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    parcel_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("parcels.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    season_name: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
    )

    rice_variety: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
    )

    start_date: Mapped[date] = mapped_column(
        Date,
        nullable=False,
    )

    expected_harvest_date: Mapped[date | None] = mapped_column(
        Date,
        nullable=True,
    )

    status: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        default="ACTIVE",
    )

    parcel: Mapped["Parcel"] = relationship(
        back_populates="crop_seasons"
    )

    disease_reports: Mapped[list["DiseaseReport"]] = relationship(
        back_populates="crop_season",
        cascade="all, delete-orphan",
    )

    treatment_applications: Mapped[list["TreatmentApplication"]] = relationship(
        back_populates="crop_season",
        cascade="all, delete-orphan",
    )
