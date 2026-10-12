// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowOperations;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Stops a farmer from piling up requests nobody has decided yet.
@Component
public class PendingRequestLimiter {

  private final WorkflowOperations workflowService;
  private final CurrentUser currentUserService;
  private final int maxPending;

  public PendingRequestLimiter(WorkflowOperations workflowService, CurrentUser currentUserService,
      @Value("${app.max-pending-requests:3}") int maxPending) {
    this.workflowService = workflowService;
    this.currentUserService = currentUserService;
    this.maxPending = maxPending;
  }

  public void requireCapacity() {
    long pending = workflowService.countPending(currentUserService.currentUserId(),
        ApprovalCaseTypes.FARMER_REQUESTS);
    if (pending >= maxPending) {
      throw new PokaYokeViolationException("TOO_MANY_PENDING_REQUESTS",
          "Bạn đang có %d yêu cầu chờ duyệt, hãy chờ quản trị viên duyệt rồi gửi tiếp"
              .formatted(pending));
    }
  }
}
