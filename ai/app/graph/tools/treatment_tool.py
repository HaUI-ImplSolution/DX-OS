# SPDX-License-Identifier: Apache-2.0
from app.services.structured_knowledge_service import lookup_structured_knowledge


def lookup(db, disease_code: str) -> dict:
    return lookup_structured_knowledge(db, disease_code)
