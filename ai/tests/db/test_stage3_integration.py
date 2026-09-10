import os

import pytest
from sqlalchemy import func, select

from app.db.models.disease_management_policy import DiseaseManagementPolicy
from app.db.models.pesticide import Pesticide
from app.db.models.treatment_rule import TreatmentRule
from app.db.repositories.knowledge_repository import (
    get_management_policy_by_disease_code,
    get_treatment_rules_by_disease_code,
)
from app.db.session import SessionLocal
from scripts.seed_disease_management_policies import (
    seed_disease_management_policies,
)
from scripts.seed_pesticides import seed_pesticides
from scripts.seed_treatment_rules import seed_treatment_rules

pytestmark = pytest.mark.skipif(
    os.getenv("RUN_STAGE3_DB_TESTS") != "1",
    reason="Set RUN_STAGE3_DB_TESTS=1 after migration and seeding.",
)


@pytest.fixture
def db():
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()


def test_reviewed_treatment_rules_are_queryable(db):
    tomato_rules = get_treatment_rules_by_disease_code(
        db,
        "Tomato___Late_blight",
    )
    assert tomato_rules
    assert all(rule.pesticide is not None for rule in tomato_rules)
    assert all(rule.dose_min <= rule.dose_max for rule in tomato_rules)
    assert all(rule.phi_days > 0 for rule in tomato_rules)

    corn_rules = get_treatment_rules_by_disease_code(
        db,
        "Corn_(maize)___Northern_Leaf_Blight",
    )
    assert len(corn_rules) == 1
    corn_rule = corn_rules[0]
    assert corn_rule.pesticide.name == "Amistar Top 325SC"
    assert (corn_rule.dose_min, corn_rule.dose_max) == (0.25, 0.5)
    assert corn_rule.dose_unit == "L/ha"
    assert corn_rule.phi_days == 10


def test_safe_management_policies_are_queryable(db):
    assert not get_treatment_rules_by_disease_code(
        db,
        "Tomato___Target_Spot",
    )
    target_policy = get_management_policy_by_disease_code(
        db,
        "Tomato___Target_Spot",
    )
    assert target_policy is not None
    assert target_policy.direct_treatment_rule_allowed is False

    virus_policy = get_management_policy_by_disease_code(
        db,
        "Tomato___Tomato_mosaic_virus",
    )
    assert virus_policy is not None
    assert virus_policy.management_status == "NO_CHEMICAL_CONTROL"


def _structured_counts(db):
    return (
        db.scalar(select(func.count()).select_from(Pesticide)),
        db.scalar(select(func.count()).select_from(TreatmentRule)),
        db.scalar(select(func.count()).select_from(DiseaseManagementPolicy)),
    )


def test_stage5_seeders_are_idempotent(db):
    seed_pesticides(db)
    seed_treatment_rules(db)
    seed_disease_management_policies(db)
    first_counts = _structured_counts(db)

    pesticide_result = seed_pesticides(db)
    rule_result = seed_treatment_rules(db)
    policy_result = seed_disease_management_policies(db)
    second_counts = _structured_counts(db)

    assert first_counts == second_counts == (4, 8, 21)
    assert pesticide_result["created"] == 0
    assert rule_result["created"] == 0
    assert policy_result["created"] == 0
