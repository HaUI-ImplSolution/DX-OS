import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session, joinedload

from ..models.disease import Disease
from ..models.pesticide import Pesticide
from ..models.treatment_rule import TreatmentRule


def get_disease_by_code(
    db: Session,
    disease_code: str,
) -> Disease | None:
    stmt = (
        select(Disease)
        .where(Disease.code == disease_code)
    )

    return db.scalar(stmt)


def get_pesticide_by_id(
    db: Session,
    pesticide_id: uuid.UUID,
) -> Pesticide | None:
    stmt = (
        select(Pesticide)
        .where(Pesticide.id == pesticide_id)
    )

    return db.scalar(stmt)


def get_treatment_rules_by_disease_code(
    db: Session,
    disease_code: str,
) -> list[TreatmentRule]:

    stmt = (
        select(TreatmentRule)
        .join(Disease)
        .where(Disease.code == disease_code)
        .options(
            joinedload(TreatmentRule.disease),
            joinedload(TreatmentRule.pesticide),
        )
    )

    return list(db.scalars(stmt).all())