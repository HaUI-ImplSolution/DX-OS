# SPDX-License-Identifier: Apache-2.0
"""Runtime dependencies for a single recommendation-graph invocation."""

from dataclasses import dataclass
from typing import Any

from sqlalchemy.orm import Session


@dataclass(frozen=True)
class WorkflowRuntime:
    """Keep infrastructure dependencies out of the serializable graph state."""

    db: Session | None = None
    vision_tool: Any = None
    field_context_tool: Any = None
    treatment_tool: Any = None
    rag_tool: Any = None
    recommendation_writer: Any = None
