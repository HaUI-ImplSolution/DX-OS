package HaUI.ImplSolution.DX_Farm.core.workflow;

import java.time.Instant;

// @formatter:off
public record WorkflowCaseResponse(
  // Primary key
  Long id,

  // Kind of process, e.g. DIAGNOSIS_APPROVAL
  String caseType,

  // Entity being processed: its type and id in the application
  String entityType,
  Long entityId,

  // PENDING, APPROVED, REJECTED or CANCELLED
  String status,

  // Token "sub" of whoever opened the case (null for older data)
  String createdBy,

  Instant createdAt,

  // Assignee (null while nobody has taken the case)
  String assignedTo,
  String assignedToName,

  Instant updatedAt
){}
