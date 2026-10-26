// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.audit;

import java.util.Map;

// One thing worth tracing. The actor, client address and request id come from the current request.
//
// action: upper-case name such as WORKSPACE_CREATED
// targetType, targetId: what the action was done to; either may be null
// metadata: extra facts to keep with the record; null or empty when there are none
public record AuditEvent(String action,String targetType,String targetId,AuditOutcome outcome,Map<String,Object>metadata){

public static AuditEvent success(String action,String targetType,String targetId){return new AuditEvent(action,targetType,targetId,AuditOutcome.SUCCESS,null);}

public static AuditEvent success(String action,String targetType,String targetId,Map<String,Object>metadata){return new AuditEvent(action,targetType,targetId,AuditOutcome.SUCCESS,metadata);}

public static AuditEvent failed(String action,String targetType,String targetId,Map<String,Object>metadata){return new AuditEvent(action,targetType,targetId,AuditOutcome.FAILED,metadata);}}
