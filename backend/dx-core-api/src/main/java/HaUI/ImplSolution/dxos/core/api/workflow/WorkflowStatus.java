// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

// Status codes of the standard approval flow (see CaseTypeDefinition.approval). A case type may
// declare any other codes; a status is always carried as a string.
public final class WorkflowStatus {

  public static final String PENDING = "PENDING";
  public static final String APPROVED = "APPROVED";
  public static final String REJECTED = "REJECTED";
  public static final String CANCELLED = "CANCELLED";

  private WorkflowStatus() {}
}
