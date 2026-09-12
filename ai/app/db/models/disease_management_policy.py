import uuid
from typing import TYPE_CHECKING

from sqlalchemy import BigInteger, Boolean, ForeignKey, String, Text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from ..base import Base

if TYPE_CHECKING:
    from .disease import Disease


class DiseaseManagementPolicy(Base):
    __tablename__ = "disease_management_policies"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    disease_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("diseases.id"),
        nullable=False,
        unique=True,
        index=True,
    )

    management_status: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
    )

    direct_treatment_rule_allowed: Mapped[bool] = mapped_column(
        Boolean,
        nullable=False,
        default=False,
    )

    chemical_rule_scope: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
    )

    source: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
    )

    version: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        default="1.0",
    )

    notes: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
    )

    disease: Mapped["Disease"] = relationship()
