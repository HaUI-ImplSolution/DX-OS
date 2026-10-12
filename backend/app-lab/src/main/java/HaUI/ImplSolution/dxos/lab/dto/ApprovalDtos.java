// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.dto;

import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowEventResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

// The queue of requests staff work through, with the entity behind each case.
public final class ApprovalDtos {

  public record ApprovalRequestResponse(Long caseId, String caseType, String status, String title,
      String workspaceCode, String requesterName, Map<String, Object> details, Instant createdAt,
      String assignedTo, String assignedToName, Instant updatedAt) {}

  public record ApprovalRequestDetailResponse(ApprovalRequestResponse request,
      List<WorkflowEventResponse> events) {}

  private ApprovalDtos() {}
}
