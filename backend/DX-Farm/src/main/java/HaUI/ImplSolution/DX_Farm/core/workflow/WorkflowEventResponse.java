// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.core.workflow;

import java.time.Instant;
import java.util.Map;

// @formatter:off
public record WorkflowEventResponse(
  // Primary key
  Long id,

  // SUBMITTED, APPROVED, REJECTED, CANCELLED, RESUBMITTED or ASSIGNED
  String action,

  // Null for the step that opens the case
  String fromStatus,
  String toStatus,

  // Actor: token "sub" and display name
  String actorUserId,
  String actorDisplayName,

  // Reason (when rejecting)
  String reason,

  Instant occurredAt,

  // Data specific to a kind of process (may be null)
  Map<String, Object> metadata
){}
