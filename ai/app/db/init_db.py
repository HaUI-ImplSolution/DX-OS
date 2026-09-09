from .base import Base
from .session import engine

from .models import (
    Parcel,
    CropSeason,
    DiseaseReport,
    Diagnosis,
    Disease,
    Pesticide,
    TreatmentRule,
    TreatmentApplication,
)


def init_db():
    print("Creating database tables...")

    Base.metadata.create_all(bind=engine)

    print("Database tables created successfully!")


if __name__ == "__main__":
    init_db()