// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.workflow;

import HaUI.ImplSolution.dxos.core.api.workflow.CaseQuery;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseView;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowOperations;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowAction;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowActor;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowDecisionHandler;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowEventResponse;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseStatus;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseTransition;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseTypeDefinition;
import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ActionNotAllowedException;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Generic workflow: a case per entity plus an append-only event log. The statuses and steps of a
// case come from the registered case type definition, and the service knows nothing about the
// entities it tracks, so every application reuses it as is.
@Service
@Transactional(readOnly = true)
public class WorkflowService implements WorkflowOperations {

  private static final int MAX_PAGE_SIZE = 100;

  private final WorkflowCaseRepository caseRepository;
  private final WorkflowEventRepository eventRepository;
  private final WorkflowDefinitions definitions;
  private final Map<String, WorkflowDecisionHandler> decisionHandlers = new HashMap<>();

  public WorkflowService(WorkflowCaseRepository caseRepository,
      WorkflowEventRepository eventRepository, WorkflowDefinitions definitions,
      List<WorkflowDecisionHandler> handlers) {
    this.caseRepository = caseRepository;
    this.eventRepository = eventRepository;
    this.definitions = definitions;
    handlers.forEach(handler -> decisionHandlers.put(handler.caseType(), handler));
  }

  @Override
  @Transactional
  public CaseView createCase(String caseType, String entityType, Long entityId,
      WorkflowActor actor) {
    return createCase(caseType, entityType, entityId, actor, null);
  }

  // metadata: data specific to the kind of process, kept on the opening event
  @Override
  @Transactional
  public CaseView createCase(String caseType, String entityType, Long entityId, WorkflowActor actor,
      Map<String, Object> metadata) {
    String initial = definitions.require(caseType).initialStatus();
    WorkflowCase created =
        caseRepository.save(WorkflowCase.builder().caseType(caseType).entityType(entityType)
            .entityId(entityId).status(initial).createdBy(actor.userId()).build());
    appendEvent(created, WorkflowAction.SUBMITTED, null, initial, actor, null, metadata);
    return toView(created);
  }

  // Moves the case of an entity to a new status and logs the step. Earlier events stay untouched.
  @Override
  @Transactional
  public CaseView recordTransition(String caseType, String entityType, Long entityId, String target,
      WorkflowActor actor, String reason, Map<String, Object> metadata) {
    WorkflowCase workflowCase =
        caseRepository.findByCaseTypeAndEntityTypeAndEntityId(caseType, entityType, entityId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Workflow case not found for %s %s".formatted(entityType, entityId)));
    return toView(move(workflowCase, target, actor, reason, metadata));
  }

  // Moves a case and hands the decision to the handler of its case type.
  @Override
  @Transactional
  public CaseView decide(Long caseId, String target, WorkflowActor actor, String reason) {
    WorkflowCase workflowCase = findOrThrow(caseId);
    WorkflowDecisionHandler handler = decisionHandlers.get(workflowCase.getCaseType());
    if (handler == null) {
      throw new InvalidStateException(
          "Case type %s is decided through its own endpoint".formatted(workflowCase.getCaseType()));
    }
    move(workflowCase, target, actor, reason, null);
    CaseView view = toView(workflowCase);
    handler.onDecision(view, target, reason);
    return view;
  }

  // Moves a case to a new status and logs the step. Earlier events stay untouched.
  private WorkflowCase move(WorkflowCase workflowCase, String target, WorkflowActor actor,
      String reason, Map<String, Object> metadata) {
    CaseTypeDefinition definition = definitions.require(workflowCase.getCaseType());
    String current = workflowCase.getStatus();
    CaseTransition transition = definition.transition(current, target)
        .orElseThrow(() -> new InvalidStateException("Workflow case %d cannot move from %s to %s"
            .formatted(workflowCase.getId(), current, target)));
    if (!transition.roles().isEmpty() && actor.roles() != null
        && Collections.disjoint(transition.roles(), actor.roles())) {
      throw new ActionNotAllowedException("Moving a %s case from %s to %s needs one of the roles %s"
          .formatted(workflowCase.getCaseType(), current, target, transition.roles()));
    }
    workflowCase.setStatus(target);
    if (target.equals(definition.initialStatus())) {
      workflowCase.setAssignedTo(null);
      workflowCase.setAssignedToName(null);
    }
    caseRepository.save(workflowCase);
    appendEvent(workflowCase, transition.action(), current, target, actor, reason, metadata);
    return workflowCase;
  }

  // Takes a pending case. Someone else's case can only be taken over when `override` is set.
  @Override
  @Transactional
  public CaseView assign(Long caseId, WorkflowActor actor, boolean override) {
    WorkflowCase workflowCase = findOrThrow(caseId);
    boolean pending = definitions.require(workflowCase.getCaseType())
        .status(workflowCase.getStatus()).map(CaseStatus::pending).orElse(false);
    if (!pending) {
      throw new InvalidStateException("Only a pending case can be assigned: " + caseId);
    }
    String owner = workflowCase.getAssignedTo();
    if (owner != null && !owner.equals(actor.userId()) && !override) {
      throw new InvalidStateException(
          "Workflow case is already assigned to " + workflowCase.getAssignedToName());
    }
    workflowCase.setAssignedTo(actor.userId());
    workflowCase.setAssignedToName(actor.displayName());
    caseRepository.save(workflowCase);
    appendEvent(workflowCase, WorkflowAction.ASSIGNED, workflowCase.getStatus(),
        workflowCase.getStatus(), actor, null, null);
    return toView(workflowCase);
  }

  @Override
  public PageResponse<CaseView> list(String caseType, String status, String assignedTo, int page,
      int size) {
    requireKnownStatus(caseType, status);
    Specification<WorkflowCase> filter = registeredCaseTypes();
    if (caseType != null) {
      filter = filter.and((root, query, cb) -> cb.equal(root.get("caseType"), caseType));
    }
    if (status != null) {
      filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    if (assignedTo != null) {
      filter = filter.and((root, query, cb) -> cb.equal(root.get("assignedTo"), assignedTo));
    }
    PageRequest pageRequest = PageRequest.of(Math.max(page, 0),
        Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by("createdAt", "id"));
    return PageResponse.of(caseRepository.findAll(filter, pageRequest), WorkflowService::toView);
  }

  // Cases of another app that stay in the database are not listed.
  private Specification<WorkflowCase> registeredCaseTypes() {
    return (root, query, cb) -> root.get("caseType").in(definitions.caseTypes());
  }

  // Query for application layers that need filters the generic list does not offer.
  @Override
  public PageResponse<CaseView> search(CaseQuery query, int page, int size) {
    requireKnownStatus(query.caseType(), query.status());
    Specification<WorkflowCase> filter = registeredCaseTypes();
    if (query.caseType() != null) {
      filter = filter.and((root, q, cb) -> cb.equal(root.get("caseType"), query.caseType()));
    }
    if (query.status() != null) {
      filter = filter.and((root, q, cb) -> cb.equal(root.get("status"), query.status()));
    }
    if (query.assignedTo() != null) {
      filter = filter.and((root, q, cb) -> cb.equal(root.get("assignedTo"), query.assignedTo()));
    }
    if (query.unassigned()) {
      filter = filter.and((root, q, cb) -> cb.isNull(root.get("assignedTo")));
    }
    if (query.createdFrom() != null) {
      filter = filter.and(
          (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), query.createdFrom()));
    }
    if (query.createdBefore() != null) {
      filter =
          filter.and((root, q, cb) -> cb.lessThan(root.get("createdAt"), query.createdBefore()));
    }
    if (query.anyOfEntities() != null) {
      filter = filter.and(anyOfEntities(query.anyOfEntities()));
    }
    PageRequest pageRequest = PageRequest.of(Math.max(page, 0),
        Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by("createdAt", "id"));
    return PageResponse.of(caseRepository.findAll(filter, pageRequest), WorkflowService::toView);
  }

  private static Specification<WorkflowCase> anyOfEntities(List<CaseQuery.EntityIds> entities) {
    Specification<WorkflowCase> match = (root, q, cb) -> cb.disjunction();
    for (CaseQuery.EntityIds entity : entities) {
      if (entity.ids().isEmpty()) {
        continue;
      }
      match =
          match.or((root, q, cb) -> cb.and(cb.equal(root.get("entityType"), entity.entityType()),
              root.get("entityId").in(entity.ids())));
    }
    return match;
  }

  @Override
  public CaseView get(Long caseId) {
    return toView(findOrThrow(caseId));
  }

  // Number of cases a user opened that are still waiting for a decision.
  @Override
  public long countPending(String createdBy, Collection<String> caseTypes) {
    return caseTypes.stream()
        .mapToLong(caseType -> caseRepository.countByCreatedByAndCaseTypeAndStatusIn(createdBy,
            caseType, definitions.require(caseType).pendingStatuses()))
        .sum();
  }

  @Override
  public boolean hasPending(String caseType, String entityType, Collection<Long> entityIds) {
    return !entityIds.isEmpty()
        && caseRepository.existsByCaseTypeAndEntityTypeAndEntityIdInAndStatusIn(caseType,
            entityType, entityIds, definitions.require(caseType).pendingStatuses());
  }

  @Override
  public Optional<CaseView> find(String caseType, String entityType, Long entityId) {
    return caseRepository.findByCaseTypeAndEntityTypeAndEntityId(caseType, entityType, entityId)
        .map(WorkflowService::toView);
  }

  @Override
  public List<WorkflowEventResponse> events(Long caseId) {
    findOrThrow(caseId);
    return eventRepository.findByWorkflowCaseIdOrderByOccurredAtAscIdAsc(caseId).stream()
        .map(WorkflowService::toResponse).toList();
  }

  private WorkflowCase findOrThrow(Long caseId) {
    return caseRepository.findById(caseId)
        .orElseThrow(() -> new ResourceNotFoundException("Workflow case not found: " + caseId));
  }

  // A status filter must name a status the case type (or, without one, any case type) declares.
  private void requireKnownStatus(String caseType, String status) {
    if (status == null) {
      return;
    }
    boolean known = caseType != null
        ? definitions.find(caseType).map(d -> d.status(status).isPresent()).orElse(false)
        : definitions.knowsStatus(status);
    if (!known) {
      throw new IllegalArgumentException("Unknown status: " + status);
    }
  }

  private void appendEvent(WorkflowCase workflowCase, String action, String from, String to,
      WorkflowActor actor, String reason, Map<String, Object> metadata) {
    eventRepository.save(WorkflowEvent.builder().workflowCase(workflowCase).action(action)
        .fromStatus(from).toStatus(to).actorUserId(actor.userId())
        .actorDisplayName(actor.displayName()).reason(reason).metadata(metadata).build());
  }

  static CaseView toView(WorkflowCase workflowCase) {
    return new CaseView(workflowCase.getId(), workflowCase.getCaseType(),
        workflowCase.getEntityType(), workflowCase.getEntityId(), workflowCase.getStatus(),
        workflowCase.getCreatedBy(), workflowCase.getCreatedAt(), workflowCase.getAssignedTo(),
        workflowCase.getAssignedToName(), workflowCase.getUpdatedAt());
  }

  static WorkflowEventResponse toResponse(WorkflowEvent event) {
    return new WorkflowEventResponse(event.getId(), event.getAction(), event.getFromStatus(),
        event.getToStatus(), event.getActorUserId(), event.getActorDisplayName(), event.getReason(),
        event.getOccurredAt(), event.getMetadata());
  }
}
