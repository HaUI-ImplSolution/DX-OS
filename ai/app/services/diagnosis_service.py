# SPDX-License-Identifier: Apache-2.0
"""Persist a prediction for an existing report, atomically with its status."""

import math
import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.models.diagnosis import Diagnosis
from app.db.models.disease import Disease
from app.db.models.disease_report import DiseaseReport
from app.services.structured_knowledge_service import lookup_structured_knowledge


def diagnose_report(
    db: Session, report_id: uuid.UUID, image_bytes: bytes, predictor
) -> dict:
    prediction = predictor.predict(image_bytes)
    return persist_prediction(db, report_id, prediction)


def persist_prediction(db: Session, report_id: uuid.UUID, prediction: dict) -> dict:
    """Persist an already-produced CV contract for one report."""

    confidence = prediction["confidence"]
    if not math.isfinite(confidence) or not 0 <= confidence <= 1:
        raise ValueError("Invalid prediction confidence")
    try:
        report = db.scalar(
            select(DiseaseReport).where(DiseaseReport.id == report_id).with_for_update()
        )
        if report is None:
            raise LookupError("Disease report not found")
        if db.scalar(select(Diagnosis).where(Diagnosis.disease_report_id == report_id)):
            raise FileExistsError("Disease report already diagnosed")
        disease = db.scalar(
            select(Disease).where(Disease.code == prediction["disease_code"])
        )
        if disease is None:
            raise ValueError("Prediction has unknown Disease.code")
        structured = lookup_structured_knowledge(db, disease.code)
        diagnosis = Diagnosis(
            disease_report_id=report.id,
            disease_id=disease.id,
            confidence=confidence,
            model_name=prediction["model_name"],
            model_version=prediction["model_version"],
        )
        db.add(diagnosis)
        report.status = "ANALYZED"
        db.flush()
        result = {
            "diagnosis_id": str(diagnosis.id),
            "report_id": str(report.id),
            "prediction": prediction,
            "structured": structured,
            "requires_engineer_approval": True,
        }
        db.commit()
        return result
    except Exception:
        db.rollback()
        raise
