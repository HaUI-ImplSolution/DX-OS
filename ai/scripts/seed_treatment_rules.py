import json
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.models.disease import Disease
from app.db.models.pesticide import Pesticide
from app.db.models.treatment_rule import TreatmentRule
from app.db.session import SessionLocal

DATA_FILE = (
    Path(__file__).resolve().parents[1] / "data" / "structured" / "treatment_rules.json"
)
REQUIRED_FIELDS = {
    "disease_code",
    "pesticide_name",
    "dose_min",
    "dose_max",
    "dose_unit",
    "phi_days",
    "source",
    "version",
    "notes",
}


def load_treatment_rules() -> list[dict]:
    data = json.loads(DATA_FILE.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise TypeError("treatment_rules.json must contain a JSON array")

    identities = set()
    for index, item in enumerate(data, start=1):
        missing = REQUIRED_FIELDS - item.keys()
        if missing:
            raise ValueError(
                f"Treatment rule {index} is missing: {', '.join(sorted(missing))}"
            )
        identity = (item["disease_code"], item["pesticide_name"])
        if identity in identities:
            raise ValueError(f"Duplicate treatment rule identity: {identity}")
        identities.add(identity)
        if item["dose_min"] > item["dose_max"]:
            raise ValueError(f"Invalid dose range for {identity}")
        if item["phi_days"] <= 0:
            raise ValueError(f"Invalid PHI for {identity}")
    return data


def _get_exactly_one(session: Session, model, condition, label: str):
    matches = list(session.scalars(select(model).where(condition)).all())
    if not matches:
        raise ValueError(f"Missing {label}")
    if len(matches) > 1:
        raise ValueError(f"Multiple records found for {label}")
    return matches[0]


def seed_treatment_rules(db: Session | None = None) -> dict[str, int]:
    owns_session = db is None
    session = db or SessionLocal()
    result = {"created": 0, "updated": 0, "unchanged": 0}

    try:
        for item in load_treatment_rules():
            disease = _get_exactly_one(
                session,
                Disease,
                Disease.code == item["disease_code"],
                f"Disease.code={item['disease_code']}",
            )
            pesticide = _get_exactly_one(
                session,
                Pesticide,
                Pesticide.name == item["pesticide_name"],
                f"Pesticide.name={item['pesticide_name']}",
            )

            matches = list(
                session.scalars(
                    select(TreatmentRule).where(
                        TreatmentRule.disease_id == disease.id,
                        TreatmentRule.pesticide_id == pesticide.id,
                    )
                ).all()
            )
            if len(matches) > 1:
                raise ValueError(
                    "Duplicate TreatmentRule rows for "
                    f"{item['disease_code']} / {item['pesticide_name']}"
                )

            values = {
                "dose_min": item["dose_min"],
                "dose_max": item["dose_max"],
                "dose_unit": item["dose_unit"],
                "phi_days": item["phi_days"],
                "source": item["source"],
                "version": item["version"],
                "notes": item["notes"],
            }
            if matches:
                rule = matches[0]
                changed = any(
                    getattr(rule, field) != value for field, value in values.items()
                )
                if changed:
                    for field, value in values.items():
                        setattr(rule, field, value)
                    result["updated"] += 1
                else:
                    result["unchanged"] += 1
                continue

            session.add(
                TreatmentRule(
                    disease_id=disease.id,
                    pesticide_id=pesticide.id,
                    **values,
                )
            )
            result["created"] += 1

        session.commit()
        return result
    except Exception:
        session.rollback()
        raise
    finally:
        if owns_session:
            session.close()


def main() -> None:
    print(seed_treatment_rules())


if __name__ == "__main__":
    main()
