import json
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.models.pesticide import Pesticide
from app.db.session import SessionLocal

DATA_FILE = (
    Path(__file__).resolve().parents[1] / "data" / "structured" / "pesticides.json"
)
REQUIRED_FIELDS = {"name", "active_ingredient", "concentration", "notes"}


def load_pesticides() -> list[dict]:
    data = json.loads(DATA_FILE.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise TypeError("pesticides.json must contain a JSON array")

    identities = set()
    for index, item in enumerate(data, start=1):
        missing = REQUIRED_FIELDS - item.keys()
        if missing:
            raise ValueError(
                f"Pesticide item {index} is missing: {', '.join(sorted(missing))}"
            )
        identity = (
            item["name"],
            item["active_ingredient"],
            item["concentration"],
        )
        if identity in identities:
            raise ValueError(f"Duplicate pesticide identity: {identity}")
        identities.add(identity)
    return data


def seed_pesticides(db: Session | None = None) -> dict[str, int]:
    owns_session = db is None
    session = db or SessionLocal()
    result = {"created": 0, "updated": 0, "unchanged": 0}

    try:
        for item in load_pesticides():
            same_name = list(
                session.scalars(
                    select(Pesticide).where(Pesticide.name == item["name"])
                ).all()
            )
            if len(same_name) > 1:
                raise ValueError(f"Multiple pesticides found with name: {item['name']}")

            if same_name:
                pesticide = same_name[0]
                current_identity = (
                    pesticide.active_ingredient,
                    pesticide.concentration,
                )
                reviewed_identity = (
                    item["active_ingredient"],
                    item["concentration"],
                )
                if current_identity != reviewed_identity:
                    raise ValueError(
                        "Pesticide identity conflict for "
                        f"{item['name']}: {current_identity!r} != "
                        f"{reviewed_identity!r}"
                    )

                if pesticide.notes != item["notes"]:
                    pesticide.notes = item["notes"]
                    result["updated"] += 1
                else:
                    result["unchanged"] += 1
                continue

            session.add(Pesticide(**item))
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
    print(seed_pesticides())


if __name__ == "__main__":
    main()
