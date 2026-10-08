// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.core.workflow;

// Applies a decision on a case to the application entity it tracks. The workflow core only finds
// the handler of a case type; it never learns what the entity is.
public interface WorkflowDecisionHandler {

  String caseType();

  // Runs in the transaction that records the decision; target is APPROVED or REJECTED.
  void onDecision(WorkflowCase workflowCase, WorkflowStatus target, String reason);
}
