import json
from pathlib import Path

from sqlalchemy import select

from app.db.models.disease import Disease
from app.db.session import SessionLocal

DATA_FILE = (
    Path(__file__).resolve().parents[1] / "data" / "structured" / "diseases.json"
)


def seed_diseases():
    with open(DATA_FILE, "r", encoding="utf-8") as f:
        diseases_data = json.load(f)

    db = SessionLocal()

    try:
        for item in diseases_data:
            existing = db.scalar(select(Disease).where(Disease.code == item["code"]))

            if existing:
                print(f"Skip: {item['code']}")
                continue

            disease = Disease(
                code=item["code"],
                name=item["name"],
                description=item.get("description"),
            )

            db.add(disease)

        db.commit()

        print("Disease seed completed.")

    except Exception:
        db.rollback()
        raise

    finally:
        db.close()


if __name__ == "__main__":
    seed_diseases()
