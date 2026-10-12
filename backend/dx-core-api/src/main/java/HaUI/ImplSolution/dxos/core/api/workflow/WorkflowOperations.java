// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Workflow services an application calls: a case per entity plus an append-only event log.
public interface WorkflowOperations {

  CaseView createCase(String caseType, String entityType, Long entityId, WorkflowActor actor);

  // metadata: data specific to the kind of process, kept on the opening event
  CaseView createCase(String caseType, String entityType, Long entityId, WorkflowActor actor,
      Map<String, Object> metadata);

  // Moves the case of an entity to a new status and logs the step.
  CaseView recordTransition(String caseType, String entityType, Long entityId,
      WorkflowStatus target, WorkflowActor actor, String reason, Map<String, Object> metadata);

  // Decides a pending case and hands the decision to the handler of its case type.
  CaseView decide(Long caseId, WorkflowStatus target, WorkflowActor actor, String reason);

  // Takes a pending case; someone else's case can only be taken over when override is set.
  CaseView assign(Long caseId, WorkflowActor actor, boolean override);

  PageResponse<CaseView> list(String caseType, WorkflowStatus status, String assignedTo, int page,
      int size);

  PageResponse<CaseView> search(CaseQuery query, int page, int size);

  CaseView get(Long caseId);

  Optional<CaseView> find(String caseType, String entityType, Long entityId);

  // Number of cases a user opened that are still waiting for a decision.
  long countPending(String createdBy, Collection<String> caseTypes);

  boolean hasPending(String caseType, String entityType, Collection<Long> entityIds);

  List<WorkflowEventResponse> events(Long caseId);
}
