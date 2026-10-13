// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

import java.util.Set;

// Who performed a workflow step: the token "sub" plus the name shown in timelines.
// roles: the roles the actor holds, checked against the roles a transition requires; null for
// steps the system takes itself, which are not checked.
public record WorkflowActor(String userId,String displayName,Set<String>roles){

public WorkflowActor(String userId,String displayName){this(userId,displayName,null);}}
