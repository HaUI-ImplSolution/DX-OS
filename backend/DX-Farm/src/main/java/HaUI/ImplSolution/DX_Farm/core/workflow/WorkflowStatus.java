package HaUI.ImplSolution.DX_Farm.core.workflow;

import java.util.EnumSet;
import java.util.Set;

// Generic lifecycle of an approvable case; application enums map onto these names.
public enum WorkflowStatus {
  PENDING, APPROVED, REJECTED, CANCELLED;

  // A rejected case may be resubmitted; approved and cancelled cases are final.
  public Set<WorkflowStatus> allowedNext() {
    return switch (this) {
      case PENDING -> EnumSet.of(APPROVED, REJECTED, CANCELLED);
      case REJECTED -> EnumSet.of(PENDING);
      case APPROVED, CANCELLED -> EnumSet.noneOf(WorkflowStatus.class);
    };
  }
}
