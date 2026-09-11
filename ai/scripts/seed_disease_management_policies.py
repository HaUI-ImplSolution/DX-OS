import json
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.models.disease import Disease
from app.db.models.disease_management_policy import DiseaseManagementPolicy
from app.db.session import SessionLocal

DATA_FILE = (
    Path(__file__).resolve().parents[1]
    / "data"
    / "structured"
    / "disease_management_policies.json"
)
REQUIRED_FIELDS = {
    "disease_code",
    "management_status",
    "direct_treatment_rule_allowed",
    "chemical_rule_scope",
    "source",
    "version",
    "notes",
}


def load_management_policies() -> list[dict]:
    data = json.loads(DATA_FILE.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise TypeError("disease_management_policies.json must contain a JSON array")

    codes = set()
    for index, item in enumerate(data, start=1):
        missing = REQUIRED_FIELDS - item.keys()
        if missing:
            raise ValueError(
                f"Management policy {index} is missing: {', '.join(sorted(missing))}"
            )
        code = item["disease_code"]
        if code in codes:
            raise ValueError(f"Duplicate management policy: {code}")
        codes.add(code)
        if item["direct_treatment_rule_allowed"] is not False:
            raise ValueError(f"Unsafe direct treatment policy for disease: {code}")
    return data


def seed_disease_management_policies(
    db: Session | None = None,
) -> dict[str, int]:
    owns_session = db is None
    session = db or SessionLocal()
    result = {"created": 0, "updated": 0, "unchanged": 0}

    try:
        for item in load_management_policies():
            disease = session.scalar(
                select(Disease).where(Disease.code == item["disease_code"])
            )
            if disease is None:
                raise ValueError(f"Missing Disease.code={item['disease_code']}")

            matches = list(
                session.scalars(
                    select(DiseaseManagementPolicy).where(
                        DiseaseManagementPolicy.disease_id == disease.id
                    )
                ).all()
            )
            if len(matches) > 1:
                raise ValueError(
                    f"Multiple policies found for Disease.code={item['disease_code']}"
                )

            values = {
                "management_status": item["management_status"],
                "direct_treatment_rule_allowed": item["direct_treatment_rule_allowed"],
                "chemical_rule_scope": item["chemical_rule_scope"],
                "source": item["source"],
                "version": item["version"],
                "notes": item["notes"],
            }
            if matches:
                policy = matches[0]
                changed = any(
                    getattr(policy, field) != value for field, value in values.items()
                )
                if changed:
                    for field, value in values.items():
                        setattr(policy, field, value)
                    result["updated"] += 1
                else:
                    result["unchanged"] += 1
                continue

            session.add(
                DiseaseManagementPolicy(
                    disease_id=disease.id,
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
    print(seed_disease_management_policies())


if __name__ == "__main__":
    main()
