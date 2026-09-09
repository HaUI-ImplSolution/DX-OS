import uuid
from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import DateTime, Float, ForeignKey, Integer, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from ..base import Base

if TYPE_CHECKING:
    from .crop_season import CropSeason
    from .pesticide import Pesticide
    from .treatment_rule import TreatmentRule


class TreatmentApplication(Base):
    __tablename__ = "treatment_applications"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    crop_season_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("crop_seasons.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    pesticide_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("pesticides.id"),
        nullable=False,
        index=True,
    )

    treatment_rule_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("treatment_rules.id"),
        nullable=True,
    )

    dose: Mapped[float] = mapped_column(
        Float,
        nullable=False,
    )

    dose_unit: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
    )

    phi_days: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
    )

    applied_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
    )

    harvest_locked_until: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
    )

    status: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        default="APPLIED",
    )

    crop_season: Mapped["CropSeason"] = relationship(
        back_populates="treatment_applications"
    )

    pesticide: Mapped["Pesticide"] = relationship()

    treatment_rule: Mapped["TreatmentRule | None"] = relationship()