// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.audit;

import HaUI.ImplSolution.dxos.core.api.audit.AuditOutcome;
import java.time.Instant;
import java.util.Map;

// One audit record as returned to an administrator.
public record AuditLogResponse(Long id,Instant occurredAt,String actorUserId,String actorName,String actorRoles,String clientId,String action,String targetType,String targetId,AuditOutcome outcome,String ip,String requestId,String httpMethod,String path,Map<String,Object>metadata){

static AuditLogResponse of(AuditLogEntry entry){return new AuditLogResponse(entry.getId(),entry.getOccurredAt(),entry.getActorUserId(),entry.getActorName(),entry.getActorRoles(),entry.getClientId(),entry.getAction(),entry.getTargetType(),entry.getTargetId(),entry.getOutcome(),entry.getIp(),entry.getRequestId(),entry.getHttpMethod(),entry.getPath(),entry.getMetadata());}}
