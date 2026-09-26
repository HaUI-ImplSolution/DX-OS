package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowService;
import HaUI.ImplSolution.DX_Farm.exception.PokaYokeViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Stops a farmer from piling up requests nobody has decided yet.
@Component
public class PendingRequestLimiter {

  private final WorkflowService workflowService;
  private final CurrentUserService currentUserService;
  private final int maxPending;

  public PendingRequestLimiter(WorkflowService workflowService,
      CurrentUserService currentUserService,
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
