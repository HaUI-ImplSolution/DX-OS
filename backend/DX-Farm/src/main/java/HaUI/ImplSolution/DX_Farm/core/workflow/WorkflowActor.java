// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.core.workflow;

// Who performed a workflow step: the token "sub" plus the name shown in timelines.
public record WorkflowActor(String userId,String displayName){}
