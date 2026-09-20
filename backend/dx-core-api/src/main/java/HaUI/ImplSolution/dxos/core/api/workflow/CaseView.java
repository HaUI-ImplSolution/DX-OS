// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

import java.time.Instant;

// Read-only snapshot of a workflow case, so applications never see the core entity.
public record CaseView(Long id,String caseType,String entityType,Long entityId,WorkflowStatus status,String createdBy,Instant createdAt,String assignedTo,String assignedToName,Instant updatedAt){}
