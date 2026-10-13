// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

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

  // A status declared by the case type, e.g. PENDING, APPROVED, REJECTED or CANCELLED
  String status,

  // Token "sub" of whoever opened the case (null for older data)
  String createdBy,

  Instant createdAt,

  // Assignee (null while nobody has taken the case)
  String assignedTo,
  String assignedToName,

  Instant updatedAt
) {

  public static WorkflowCaseResponse of(CaseView view) {
    return new WorkflowCaseResponse(view.id(), view.caseType(), view.entityType(), view.entityId(),
        view.status(), view.createdBy(), view.createdAt(), view.assignedTo(),
        view.assignedToName(), view.updatedAt());
  }
}
