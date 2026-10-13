// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

// Names of the steps the workflow core writes itself; a transition may declare any other action.
public final class WorkflowAction {

  public static final String SUBMITTED = "SUBMITTED";
  public static final String APPROVED = "APPROVED";
  public static final String REJECTED = "REJECTED";
  public static final String CANCELLED = "CANCELLED";
  public static final String RESUBMITTED = "RESUBMITTED";
  public static final String ASSIGNED = "ASSIGNED";

  private WorkflowAction() {}
}
