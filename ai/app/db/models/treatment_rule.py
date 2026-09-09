import uuid
from typing import TYPE_CHECKING

from sqlalchemy import Float, ForeignKey, Integer, String, Text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from ..base import Base

if TYPE_CHECKING:
    from .disease import Disease
    from .pesticide import Pesticide


class TreatmentRule(Base):
    __tablename__ = "treatment_rules"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    disease_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("diseases.id"),
        nullable=False,
        index=True,
    )

    pesticide_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("pesticides.id"),
        nullable=False,
        index=True,
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

    source: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
    )

    version: Mapped[str] = mapped_column(
        String(50),
        default="1.0",
        nullable=False,
    )

    notes: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
    )

    disease: Mapped["Disease"] = relationship()

    pesticide: Mapped["Pesticide"] = relationship()