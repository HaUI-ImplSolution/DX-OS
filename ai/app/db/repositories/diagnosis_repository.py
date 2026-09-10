import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models.diagnosis import Diagnosis
from ..models.disease import Disease
from ..models.disease_report import DiseaseReport


def create_disease_report(
    db: Session,
    crop_season_id: uuid.UUID,
    image_url: str,
) -> DiseaseReport:

    report = DiseaseReport(
        crop_season_id=crop_season_id,
        image_url=image_url,
        status="PENDING",
    )

    db.add(report)
    db.commit()
    db.refresh(report)

    return report


def create_diagnosis_from_cv(
    db: Session,
    disease_report_id: uuid.UUID,
    cv_result: dict,
) -> Diagnosis:

    disease_code = cv_result["disease_code"]

    stmt = select(Disease).where(Disease.code == disease_code)

    disease = db.scalar(stmt)

    if disease is None:
        raise ValueError(f"Disease code '{disease_code}' does not exist")

    diagnosis = Diagnosis(
        disease_report_id=disease_report_id,
        disease_id=disease.id,
        confidence=cv_result["confidence"],
        model_version=cv_result["model_version"],
        model_name=cv_result.get("model_name"),
    )

    db.add(diagnosis)

    report = db.get(
        DiseaseReport,
        disease_report_id,
    )

    if report:
        report.status = "ANALYZED"

    db.commit()
    db.refresh(diagnosis)

    return diagnosis


def get_latest_diagnosis(
    db: Session,
    crop_season_id: uuid.UUID,
) -> Diagnosis | None:

    stmt = (
        select(Diagnosis)
        .join(DiseaseReport)
        .where(DiseaseReport.crop_season_id == crop_season_id)
        .order_by(Diagnosis.diagnosed_at.desc())
        .limit(1)
    )

    return db.scalar(stmt)
