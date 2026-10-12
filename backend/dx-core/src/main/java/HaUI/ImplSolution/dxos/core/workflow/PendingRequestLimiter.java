// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.workflow;

import HaUI.ImplSolution.dxos.core.api.app.AppModule;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.api.workflow.PendingRequestLimit;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowOperations;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Counts the waiting requests of the case types the running app declares as limited.
@Component
public class PendingRequestLimiter implements PendingRequestLimit {

  private final WorkflowOperations workflow;
  private final UserContext userContext;
  private final List<String> limitedCaseTypes;
  private final int maxPending;

  public PendingRequestLimiter(WorkflowOperations workflow, UserContext userContext,
      ObjectProvider<AppModule> appModules,
      @Value("${app.max-pending-requests:3}") int maxPending) {
    this.workflow = workflow;
    this.userContext = userContext;
    this.limitedCaseTypes =
        appModules.orderedStream().flatMap(module -> module.limitedCaseTypes().stream()).toList();
    this.maxPending = maxPending;
  }

  @Override
  public void requireCapacity() {
    long pending = workflow.countPending(userContext.currentUserId(), limitedCaseTypes);
    if (pending >= maxPending) {
      throw new PokaYokeViolationException("TOO_MANY_PENDING_REQUESTS",
          "Bạn đang có %d yêu cầu chờ duyệt, hãy chờ quản trị viên duyệt rồi gửi tiếp"
              .formatted(pending));
    }
  }
}
