# SPDX-License-Identifier: Apache-2.0
from sqlalchemy.orm import Session

from app.db.repositories.knowledge_repository import (
    get_disease_by_code,
    get_management_policy_by_disease_code,
    get_treatment_rules_by_disease_code,
)


def lookup_structured_knowledge(db: Session, disease_code: str) -> dict:
    disease = get_disease_by_code(db, disease_code)
    if disease is None:
        raise ValueError(f"Unknown Disease.code: {disease_code}")
    rules = get_treatment_rules_by_disease_code(db, disease_code)
    policy = get_management_policy_by_disease_code(db, disease_code)
    # A restrictive policy always blocks chemical proposals.
    if policy is not None and not policy.direct_treatment_rule_allowed:
        rules = []
    if rules:
        return {
            "path": "treatment",
            "disease_code": disease_code,
            "policy": None,
            "rules": [
                {
                    "id": str(r.id),
                    "pesticide": r.pesticide.name,
                    "active_ingredient": r.pesticide.active_ingredient,
                    "dose_min": r.dose_min,
                    "dose_max": r.dose_max,
                    "dose_unit": r.dose_unit,
                    "phi_days": r.phi_days,
                    "source": r.source,
                    "version": r.version,
                    "notes": r.notes,
                }
                for r in rules
            ],
        }
    return {
        "path": "policy" if policy else "review_required",
        "disease_code": disease_code,
        "rules": [],
        "policy": None
        if policy is None
        else {
            "management_status": policy.management_status,
            "direct_treatment_rule_allowed": policy.direct_treatment_rule_allowed,
            "chemical_rule_scope": policy.chemical_rule_scope,
            "source": policy.source,
            "version": policy.version,
            "notes": policy.notes,
        },
    }
