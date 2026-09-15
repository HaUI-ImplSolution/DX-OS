"""Application facade for durable workflow-case state transitions."""

import uuid
from datetime import datetime
from typing import Any

from sqlalchemy.orm import Session

from app.services.workflow_service import (
    apply_workflow_case,
    approve_workflow_case,
    get_workflow_case,
    reject_workflow_case,
    serialize_case,
)


class WorkflowTransitions:
    """Expose durable case operations without coupling HTTP handlers to ORM work."""

    def __init__(self, db: Session):
        self.db = db

    def get(self, case_id: uuid.UUID) -> dict[str, Any]:
        return serialize_case(get_workflow_case(self.db, case_id))

    def approve(
        self,
        case_id: uuid.UUID,
        *,
        actor_id: str,
        selected_rule_id: uuid.UUID | None,
        approved_dose: float | None,
        confirmed_low_confidence: bool,
    ) -> dict[str, Any]:
        return serialize_case(
            approve_workflow_case(
                self.db,
                case_id,
                actor_id=actor_id,
                selected_rule_id=selected_rule_id,
                approved_dose=approved_dose,
                confirmed_low_confidence=confirmed_low_confidence,
            )
        )

    def reject(
        self, case_id: uuid.UUID, *, actor_id: str, reason: str
    ) -> dict[str, Any]:
        return serialize_case(
            reject_workflow_case(self.db, case_id, actor_id=actor_id, reason=reason)
        )

    def apply(
        self,
        case_id: uuid.UUID,
        *,
        actor_id: str,
        actual_dose: float,
        applied_at: datetime,
    ) -> dict[str, Any]:
        return serialize_case(
            apply_workflow_case(
                self.db,
                case_id,
                actor_id=actor_id,
                actual_dose=actual_dose,
                applied_at=applied_at,
            )
        )
