// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.core.workflow;

import HaUI.ImplSolution.DX_Farm.dto.PageResponse;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Generic approval workflow: a case per entity plus an append-only event log. It knows nothing
// about the entities it tracks, so every application reuses it as is.
@Service
@Transactional(readOnly = true)
public class WorkflowService {

  private static final int MAX_PAGE_SIZE = 100;

  private final WorkflowCaseRepository caseRepository;
  private final WorkflowEventRepository eventRepository;
  private final Map<String, WorkflowDecisionHandler> decisionHandlers = new HashMap<>();

  public WorkflowService(WorkflowCaseRepository caseRepository,
      WorkflowEventRepository eventRepository, List<WorkflowDecisionHandler> handlers) {
    this.caseRepository = caseRepository;
    this.eventRepository = eventRepository;
    handlers.forEach(handler -> decisionHandlers.put(handler.caseType(), handler));
  }

  @Transactional
  public WorkflowCase createCase(String caseType, String entityType, Long entityId,
      WorkflowActor actor) {
    return createCase(caseType, entityType, entityId, actor, null);
  }

  // metadata: data specific to the kind of process, kept on the opening event
  @Transactional
  public WorkflowCase createCase(String caseType, String entityType, Long entityId,
      WorkflowActor actor, Map<String, Object> metadata) {
    WorkflowCase created =
        caseRepository.save(WorkflowCase.builder().caseType(caseType).entityType(entityType)
            .entityId(entityId).status(WorkflowStatus.PENDING).createdBy(actor.userId()).build());
    appendEvent(created, WorkflowAction.SUBMITTED, null, WorkflowStatus.PENDING, actor, null,
        metadata);
    return created;
  }

  // Moves the case of an entity to a new status and logs the step. Earlier events stay untouched.
  @Transactional
  public WorkflowCase recordTransition(String caseType, String entityType, Long entityId,
      WorkflowStatus target, WorkflowActor actor, String reason, Map<String, Object> metadata) {
    WorkflowCase workflowCase =
        caseRepository.findByCaseTypeAndEntityTypeAndEntityId(caseType, entityType, entityId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Workflow case not found for %s %s".formatted(entityType, entityId)));
    return move(workflowCase, target, actor, reason, metadata);
  }

  // Decides a pending case and hands the decision to the handler of its case type.
  @Transactional
  public WorkflowCaseResponse decide(Long caseId, WorkflowStatus target, WorkflowActor actor,
      String reason) {
    if (target != WorkflowStatus.APPROVED && target != WorkflowStatus.REJECTED) {
      throw new IllegalArgumentException("A decision is APPROVED or REJECTED");
    }
    WorkflowCase workflowCase = findOrThrow(caseId);
    WorkflowDecisionHandler handler = decisionHandlers.get(workflowCase.getCaseType());
    if (handler == null) {
      throw new InvalidStateException(
          "Case type %s is decided through its own endpoint".formatted(workflowCase.getCaseType()));
    }
    move(workflowCase, target, actor, reason, null);
    handler.onDecision(workflowCase, target, reason);
    return toResponse(workflowCase);
  }

  // Moves a case to a new status and logs the step. Earlier events stay untouched.
  private WorkflowCase move(WorkflowCase workflowCase, WorkflowStatus target, WorkflowActor actor,
      String reason, Map<String, Object> metadata) {
    WorkflowStatus current = workflowCase.getStatus();
    if (!current.allowedNext().contains(target)) {
      throw new InvalidStateException("Workflow case %d cannot move from %s to %s"
          .formatted(workflowCase.getId(), current, target));
    }
    workflowCase.setStatus(target);
    if (target == WorkflowStatus.PENDING) {
      workflowCase.setAssignedTo(null);
      workflowCase.setAssignedToName(null);
    }
    caseRepository.save(workflowCase);
    appendEvent(workflowCase, actionFor(current, target), current, target, actor, reason, metadata);
    return workflowCase;
  }

  // Takes a pending case. Someone else's case can only be taken over when `override` is set.
  @Transactional
  public WorkflowCaseResponse assign(Long caseId, WorkflowActor actor, boolean override) {
    WorkflowCase workflowCase = findOrThrow(caseId);
    if (workflowCase.getStatus() != WorkflowStatus.PENDING) {
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
    return toResponse(workflowCase);
  }

  public PageResponse<WorkflowCaseResponse> list(String caseType, WorkflowStatus status,
      String assignedTo, int page, int size) {
    Specification<WorkflowCase> filter = Specification.unrestricted();
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
    return PageResponse.of(caseRepository.findAll(filter, pageRequest),
        WorkflowService::toResponse);
  }

  // Free-form query for application layers that need filters the generic list does not offer.
  public Page<WorkflowCase> search(Specification<WorkflowCase> filter, int page, int size) {
    PageRequest pageRequest = PageRequest.of(Math.max(page, 0),
        Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by("createdAt", "id"));
    return caseRepository.findAll(filter, pageRequest);
  }

  public WorkflowCase get(Long caseId) {
    return findOrThrow(caseId);
  }

  // Number of cases a user opened that are still waiting for a decision.
  public long countPending(String createdBy, Collection<String> caseTypes) {
    return caseRepository.countByCreatedByAndStatusAndCaseTypeIn(createdBy, WorkflowStatus.PENDING,
        caseTypes);
  }

  public boolean hasPending(String caseType, String entityType, Collection<Long> entityIds) {
    return !entityIds.isEmpty()
        && caseRepository.existsByCaseTypeAndEntityTypeAndEntityIdInAndStatus(caseType, entityType,
            entityIds, WorkflowStatus.PENDING);
  }

  public Optional<WorkflowCase> find(String caseType, String entityType, Long entityId) {
    return caseRepository.findByCaseTypeAndEntityTypeAndEntityId(caseType, entityType, entityId);
  }

  public List<WorkflowEventResponse> events(Long caseId) {
    findOrThrow(caseId);
    return eventRepository.findByWorkflowCaseIdOrderByOccurredAtAscIdAsc(caseId).stream()
        .map(WorkflowService::toResponse).toList();
  }

  private WorkflowCase findOrThrow(Long caseId) {
    return caseRepository.findById(caseId)
        .orElseThrow(() -> new ResourceNotFoundException("Workflow case not found: " + caseId));
  }

  private void appendEvent(WorkflowCase workflowCase, WorkflowAction action, WorkflowStatus from,
      WorkflowStatus to, WorkflowActor actor, String reason, Map<String, Object> metadata) {
    eventRepository.save(WorkflowEvent.builder().workflowCase(workflowCase).action(action)
        .fromStatus(from).toStatus(to).actorUserId(actor.userId())
        .actorDisplayName(actor.displayName()).reason(reason).metadata(metadata).build());
  }

  private static WorkflowAction actionFor(WorkflowStatus from, WorkflowStatus to) {
    if (from == WorkflowStatus.REJECTED && to == WorkflowStatus.PENDING) {
      return WorkflowAction.RESUBMITTED;
    }
    return WorkflowAction.valueOf(to.name());
  }

  static WorkflowCaseResponse toResponse(WorkflowCase workflowCase) {
    return new WorkflowCaseResponse(workflowCase.getId(), workflowCase.getCaseType(),
        workflowCase.getEntityType(), workflowCase.getEntityId(), workflowCase.getStatus().name(),
        workflowCase.getCreatedBy(), workflowCase.getCreatedAt(), workflowCase.getAssignedTo(),
        workflowCase.getAssignedToName(), workflowCase.getUpdatedAt());
  }

  static WorkflowEventResponse toResponse(WorkflowEvent event) {
    return new WorkflowEventResponse(event.getId(), event.getAction().name(),
        event.getFromStatus() != null ? event.getFromStatus().name() : null,
        event.getToStatus().name(), event.getActorUserId(), event.getActorDisplayName(),
        event.getReason(), event.getOccurredAt(), event.getMetadata());
  }
}
