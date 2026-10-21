// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ActionNotAllowedException;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.storage.FileStore;
import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseView;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowActor;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowEventResponse;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowOperations;
import HaUI.ImplSolution.dxos.lab.LabWorkflowConfig;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.AssignRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.ConfirmRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.RejectRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.ReopenRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.ResolveRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TechnicianLoadResponse;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TicketAttachmentResponse;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TicketCommentRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TicketCommentResponse;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TicketCreateRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TicketDetailResponse;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TicketResponse;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TicketUpdateRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.WaitRequest;
import HaUI.ImplSolution.dxos.lab.model.Device;
import HaUI.ImplSolution.dxos.lab.model.Room;
import HaUI.ImplSolution.dxos.lab.model.SlaPolicy;
import HaUI.ImplSolution.dxos.lab.model.Ticket;
import HaUI.ImplSolution.dxos.lab.model.TicketAttachment;
import HaUI.ImplSolution.dxos.lab.model.TicketCategory;
import HaUI.ImplSolution.dxos.lab.model.TicketComment;
import HaUI.ImplSolution.dxos.lab.model.TicketPriority;
import HaUI.ImplSolution.dxos.lab.model.TicketStatus;
import HaUI.ImplSolution.dxos.lab.repository.DeviceRepository;
import HaUI.ImplSolution.dxos.lab.repository.RoomRepository;
import HaUI.ImplSolution.dxos.lab.repository.TicketAttachmentRepository;
import HaUI.ImplSolution.dxos.lab.repository.TicketCategoryRepository;
import HaUI.ImplSolution.dxos.lab.repository.TicketCommentRepository;
import HaUI.ImplSolution.dxos.lab.repository.TicketRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// The ticket lifecycle. The workflow case is the source of truth for the status; the ticket row
// mirrors it through the decision handler so lists and statistics need no join into the core.
@Service
@Transactional(readOnly = true)
public class TicketService {

  public static final int MAX_ATTACHMENTS = 5;
  public static final int MAX_REOPENS = 3;
  public static final List<String> OPEN_STATUSES = List.of(TicketStatus.NEW, TicketStatus.ASSIGNED,
      TicketStatus.IN_PROGRESS, TicketStatus.WAITING_USER);

  // Step shown when an administrator gives a new ticket to a technician
  private static final String ASSIGNED_BY = "ASSIGNED_BY";

  private static final int MAX_PAGE_SIZE = 100;

  private final TicketRepository ticketRepository;
  private final TicketCategoryRepository categoryRepository;
  private final TicketCommentRepository commentRepository;
  private final TicketAttachmentRepository attachmentRepository;
  private final RoomRepository roomRepository;
  private final DeviceRepository deviceRepository;
  private final SlaPolicyService slaPolicyService;
  private final TicketCodeGenerator codeGenerator;
  private final WorkflowOperations workflow;
  private final FileStore fileStore;
  private final ObjectStorage objectStorage;
  private final LabUser user;
  private final LabNotifier notifier;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final int deviceAlertThreshold;
  private final int deviceAlertWindowDays;

  public TicketService(TicketRepository ticketRepository,
      TicketCategoryRepository categoryRepository, TicketCommentRepository commentRepository,
      TicketAttachmentRepository attachmentRepository, RoomRepository roomRepository,
      DeviceRepository deviceRepository, SlaPolicyService slaPolicyService,
      TicketCodeGenerator codeGenerator, WorkflowOperations workflow, FileStore fileStore,
      ObjectStorage objectStorage, LabUser user, LabNotifier notifier,
      ApplicationEventPublisher events, Clock clock,
      @Value("${lab.device-alert.threshold:3}") int deviceAlertThreshold,
      @Value("${lab.device-alert.window-days:30}") int deviceAlertWindowDays) {
    this.ticketRepository = ticketRepository;
    this.categoryRepository = categoryRepository;
    this.commentRepository = commentRepository;
    this.attachmentRepository = attachmentRepository;
    this.roomRepository = roomRepository;
    this.deviceRepository = deviceRepository;
    this.slaPolicyService = slaPolicyService;
    this.codeGenerator = codeGenerator;
    this.workflow = workflow;
    this.fileStore = fileStore;
    this.objectStorage = objectStorage;
    this.user = user;
    this.notifier = notifier;
    this.events = events;
    this.clock = clock;
    this.deviceAlertThreshold = deviceAlertThreshold;
    this.deviceAlertWindowDays = deviceAlertWindowDays;
  }

  // The SLA deadlines are fixed from the policy of the chosen priority at creation time.
  @Transactional
  public TicketDetailResponse create(TicketCreateRequest request, List<MultipartFile> files,
      UUID clientRequestId) {
    if (clientRequestId != null) {
      var existing = ticketRepository.findByClientRequestId(clientRequestId);
      if (existing.isPresent()) {
        return detailOf(existing.get());
      }
    }
    List<MultipartFile> attachments = files == null ? List.of()
        : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
    if (attachments.size() > MAX_ATTACHMENTS) {
      throw new IllegalArgumentException(
          "A ticket takes at most %d attachments".formatted(MAX_ATTACHMENTS));
    }
    TicketCategory category = categoryRepository.findByCodeAndActiveTrue(request.categoryCode())
        .orElseThrow(() -> new IllegalArgumentException(
            "Unknown ticket category: " + request.categoryCode()));
    TicketPriority priority =
        request.priority() != null ? request.priority() : category.getDefaultPriority();
    SlaPolicy sla = slaPolicyService.require(priority);
    Instant now = clock.instant();
    Ticket ticket = Ticket.builder()
        .code(codeGenerator.next(ZonedDateTime.ofInstant(now, clock.getZone()).getYear()))
        .title(request.title().strip()).description(request.description().strip())
        .category(category).priority(priority).room(roomOf(request.roomId()))
        .roomNote(noteOf(request.roomId(), request.roomNote())).device(deviceOf(request.deviceId()))
        .workspaceId(user.workspaceId()).requesterId(user.id()).requesterName(user.name())
        .status(TicketStatus.NEW).responseDueAt(now.plusSeconds(sla.getResponseMinutes() * 60L))
        .resolutionDueAt(now.plusSeconds(sla.getResolutionMinutes() * 60L))
        .clientRequestId(clientRequestId).build();
    Ticket saved = ticketRepository.save(ticket);
    for (MultipartFile file : attachments) {
      store(saved, file);
    }
    CaseView workflowCase = workflow.createCase(LabCaseTypes.TICKET, LabCaseTypes.TICKET_ENTITY,
        saved.getId(), user.actor());
    saved.setWorkflowCaseId(workflowCase.id());
    ticketRepository.save(saved);
    events.publishEvent(new LabEvents.TicketCreated(saved.getId(), saved.getCode(),
        saved.getTitle(), category.getName(), priority.name(), saved.getRequesterName(), now,
        firstImageKey(saved)));
    watchDevice(saved, now);
    return detailOf(saved);
  }

  // Tells the technicians once when the ticket is the threshold-th one on its device within the
  // window.
  private void watchDevice(Ticket ticket, Instant now) {
    Device device = ticket.getDevice();
    if (device == null) {
      return;
    }
    long count = ticketRepository.countByDeviceIdAndCreatedAtGreaterThanEqual(device.getId(),
        now.minus(Duration.ofDays(deviceAlertWindowDays)));
    if (count == deviceAlertThreshold) {
      events.publishEvent(new LabEvents.DeviceRepeatedFault(ticket.getId(), ticket.getCode(),
          device.getAssetCode(), device.getDeviceType(), count, deviceAlertWindowDays));
    }
  }

  public PageResponse<TicketResponse> list(TicketFilter filter, int page, int size) {
    Specification<Ticket> spec = Specification.unrestricted();
    if (!user.isStaff()) {
      spec = spec.and(requesterIs(user.id()));
    } else if (filter.requesterId() != null) {
      spec = spec.and(requesterIs(filter.requesterId()));
    }
    if (filter.status() != null) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), filter.status()));
    }
    if (filter.categoryCode() != null) {
      spec = spec
          .and((root, q, cb) -> cb.equal(root.get("category").get("code"), filter.categoryCode()));
    }
    if (filter.priority() != null) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("priority"), filter.priority()));
    }
    if (filter.assigneeId() != null) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("assigneeId"), filter.assigneeId()));
    }
    if (filter.unassigned()) {
      spec = spec.and((root, q, cb) -> cb.isNull(root.get("assigneeId")));
    }
    if (filter.overdue() != null) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("overdue"), filter.overdue()));
    }
    PageRequest pageRequest =
        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    return PageResponse.of(ticketRepository.findAll(spec, pageRequest), TicketResponse::of);
  }

  public TicketDetailResponse detail(Long id) {
    return detailOf(findVisible(id));
  }

  public List<TechnicianLoadResponse> technicianLoad() {
    return ticketRepository.technicianLoad(OPEN_STATUSES).stream()
        .map(row -> new TechnicianLoadResponse(row.getUserId(), row.getName(),
            row.getOpenTickets() == null ? 0 : row.getOpenTickets()))
        .toList();
  }

  // A reply of the requester while the ticket waits for them puts it back to IN_PROGRESS; the
  // first public reply of staff counts as the first response.
  @Transactional
  public TicketCommentResponse addComment(Long id, TicketCommentRequest request) {
    Ticket ticket = findVisible(id);
    requireNotFinished(ticket);
    boolean internal = Boolean.TRUE.equals(request.internal());
    if (internal && !user.isStaff()) {
      throw new ActionNotAllowedException("Only staff can write internal notes");
    }
    boolean requester = ticket.getRequesterId().equals(user.id());
    TicketComment comment = saveComment(ticket, request.body().strip(), internal);
    Instant now = clock.instant();
    if (user.isStaff() && !requester && !internal && ticket.getFirstRespondedAt() == null) {
      ticket.setFirstRespondedAt(now);
      ticketRepository.save(ticket);
    }
    if (requester && TicketStatus.WAITING_USER.equals(ticket.getStatus())) {
      workflow.decide(ticket.getWorkflowCaseId(), TicketStatus.IN_PROGRESS, user.actor(), null);
    }
    if (!internal) {
      String recipient = requester ? ticket.getAssigneeId() : ticket.getRequesterId();
      notifier.tell(recipient, LabNotificationTypes.TICKET_REPLIED, "Có phản hồi mới",
          "Yêu cầu %s có phản hồi mới từ %s.".formatted(ticket.getCode(), user.name()),
          LabCaseTypes.TICKET_ENTITY, ticket.getId());
    }
    return toResponse(comment);
  }

  // Only the requester edits a report, and only while it is open.
  @Transactional
  public TicketAttachmentResponse addAttachment(Long id, MultipartFile file) {
    Ticket ticket = findEditable(id);
    if (attachmentRepository.countByTicket_Id(id) >= MAX_ATTACHMENTS) {
      throw new IllegalArgumentException(
          "A ticket takes at most %d attachments".formatted(MAX_ATTACHMENTS));
    }
    TicketAttachmentResponse stored = toResponse(store(ticket, file));
    resumeAfterSupplement(ticket, "đã thêm ảnh");
    return stored;
  }

  @Transactional
  public void removeAttachment(Long id, Long attachmentId) {
    Ticket ticket = findEditable(id);
    TicketAttachment attachment = attachmentRepository.findById(attachmentId)
        .filter(found -> found.getTicket().getId().equals(id))
        .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));
    attachmentRepository.delete(attachment);
    resumeAfterSupplement(ticket, "đã xoá một ảnh");
  }

  @Transactional
  public TicketResponse update(Long id, TicketUpdateRequest request) {
    Ticket ticket = findEditable(id);
    ticket.setTitle(request.title().strip());
    ticket.setDescription(request.description().strip());
    ticket.setRoom(roomOf(request.roomId()));
    ticket.setRoomNote(noteOf(request.roomId(), request.roomNote()));
    ticket.setDevice(deviceOf(request.deviceId()));
    ticketRepository.save(ticket);
    saveComment(ticket, "Người gửi đã cập nhật nội dung yêu cầu.", false);
    if (!resumeAfterSupplement(ticket, "đã cập nhật nội dung")) {
      tellAssignee(ticket, "Người gửi đã cập nhật yêu cầu",
          "Yêu cầu %s: người gửi đã cập nhật nội dung.");
    }
    return TicketResponse.of(ticket);
  }

  // A technician takes a new ticket. A second technician asking at the same moment gets a 409.
  @Transactional
  public TicketResponse accept(Long id) {
    Ticket ticket = findForUpdateAction(id);
    requireStatus(ticket, TicketStatus.NEW);
    workflow.assign(ticket.getWorkflowCaseId(), user.actor(), false);
    workflow.decide(ticket.getWorkflowCaseId(), TicketStatus.ASSIGNED, user.actor(), null);
    return TicketResponse.of(ticket);
  }

  // An administrator hands a ticket that is still being worked on to a technician.
  @Transactional
  public TicketResponse assign(Long id, AssignRequest request) {
    Ticket ticket = findForUpdateAction(id);
    if (!List.of(TicketStatus.NEW, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS)
        .contains(ticket.getStatus())) {
      throw new InvalidStateException(
          "A ticket in status %s cannot be assigned".formatted(ticket.getStatus()));
    }
    WorkflowActor assignee =
        new WorkflowActor(request.assigneeId().strip(), request.assigneeName().strip());
    CaseView assigned = workflow.assign(ticket.getWorkflowCaseId(), assignee, true);
    if (TicketStatus.NEW.equals(ticket.getStatus())) {
      workflow.decide(ticket.getWorkflowCaseId(), TicketStatus.ASSIGNED, user.actor(), null);
    } else {
      ticket.setAssigneeId(assigned.assignedTo());
      ticket.setAssigneeName(assigned.assignedToName());
      ticketRepository.save(ticket);
    }
    saveComment(ticket, "%s đã gán yêu cầu cho %s.".formatted(user.name(), assignee.displayName()),
        true);
    notifier.tell(assignee.userId(), LabNotificationTypes.TICKET_ASSIGNED, "Bạn được gán yêu cầu",
        "Yêu cầu %s: %s được gán cho bạn.".formatted(ticket.getCode(), ticket.getTitle()),
        LabCaseTypes.TICKET_ENTITY, ticket.getId());
    return TicketResponse.of(ticket);
  }

  @Transactional
  public TicketResponse start(Long id) {
    Ticket ticket = findForUpdateAction(id);
    requireStatus(ticket, TicketStatus.ASSIGNED);
    requireAssigneeOrAdmin(ticket);
    workflow.decide(ticket.getWorkflowCaseId(), TicketStatus.IN_PROGRESS, user.actor(), null);
    return TicketResponse.of(ticket);
  }

  // The technician needs something from the requester: the question is posted as a public reply.
  @Transactional
  public TicketResponse waitForUser(Long id, WaitRequest request) {
    Ticket ticket = findForUpdateAction(id);
    requireStatus(ticket, TicketStatus.IN_PROGRESS);
    requireAssigneeOrAdmin(ticket);
    String message = request == null ? null : request.message();
    if (message == null || message.isBlank()) {
      throw new PokaYokeViolationException("WAIT_MESSAGE_REQUIRED",
          "Hãy ghi rõ cần người gửi bổ sung điều gì");
    }
    saveComment(ticket, message.strip(), false);
    if (ticket.getFirstRespondedAt() == null) {
      ticket.setFirstRespondedAt(clock.instant());
      ticketRepository.save(ticket);
    }
    workflow.decide(ticket.getWorkflowCaseId(), TicketStatus.WAITING_USER, user.actor(), null);
    return TicketResponse.of(ticket);
  }

  @Transactional
  public TicketResponse resolve(Long id, ResolveRequest request) {
    Ticket ticket = findForUpdateAction(id);
    requireStatus(ticket, TicketStatus.IN_PROGRESS);
    requireAssigneeOrAdmin(ticket);
    if (request == null || isBlank(request.resolutionNote())) {
      throw new PokaYokeViolationException("RESOLUTION_NOTE_REQUIRED",
          "Cần ghi chú cách đã xử lý trước khi hoàn tất");
    }
    if (isBlank(request.rootCause())) {
      throw new PokaYokeViolationException("ROOT_CAUSE_REQUIRED",
          "Cần ghi nguyên nhân gốc trước khi hoàn tất");
    }
    ticket.setResolutionNote(request.resolutionNote().strip());
    ticket.setRootCause(request.rootCause().strip());
    workflow.decide(ticket.getWorkflowCaseId(), TicketStatus.RESOLVED, user.actor(), null);
    return TicketResponse.of(ticket);
  }

  // The requester accepts the result and rates the service.
  @Transactional
  public TicketResponse confirm(Long id, ConfirmRequest request) {
    Ticket ticket = findForUpdateAction(id);
    requireStatus(ticket, TicketStatus.RESOLVED);
    requireRequester(ticket);
    Integer rating = request == null ? null : request.rating();
    if (rating == null || rating < 1 || rating > 5) {
      throw new PokaYokeViolationException("RATING_REQUIRED",
          "Hãy đánh giá từ 1 đến 5 sao trước khi xác nhận");
    }
    ticket.setRating(rating);
    ticket.setRatingComment(isBlank(request.comment()) ? null : request.comment().strip());
    workflow.decide(ticket.getWorkflowCaseId(), TicketStatus.CLOSED, user.actor(), null);
    return TicketResponse.of(ticket);
  }

  // A ticket can be reopened a limited number of times; a repeated failure needs a new look.
  @Transactional
  public TicketResponse reopen(Long id, ReopenRequest request) {
    Ticket ticket = findForUpdateAction(id);
    requireStatus(ticket, TicketStatus.RESOLVED);
    requireRequester(ticket);
    if (ticket.getReopenCount() >= MAX_REOPENS) {
      throw new PokaYokeViolationException("TICKET_REOPEN_LIMIT",
          "Yêu cầu đã được mở lại %d lần, hãy tạo yêu cầu mới".formatted(MAX_REOPENS));
    }
    String reason = request == null || isBlank(request.reason()) ? null : request.reason().strip();
    if (reason != null) {
      saveComment(ticket, "Mở lại: " + reason, false);
    }
    workflow.decide(ticket.getWorkflowCaseId(), TicketStatus.IN_PROGRESS, user.actor(), reason);
    return TicketResponse.of(ticket);
  }

  @Transactional
  public TicketResponse cancel(Long id) {
    Ticket ticket = findForUpdateAction(id);
    requireStatus(ticket, TicketStatus.NEW);
    if (!user.isAdmin()) {
      requireRequester(ticket);
    }
    workflow.decide(ticket.getWorkflowCaseId(), TicketStatus.CANCELLED, user.actor(), null);
    return TicketResponse.of(ticket);
  }

  @Transactional
  public TicketResponse reject(Long id, RejectRequest request) {
    Ticket ticket = findForUpdateAction(id);
    if (!List.of(TicketStatus.NEW, TicketStatus.ASSIGNED).contains(ticket.getStatus())) {
      throw new InvalidStateException(
          "A ticket in status %s cannot be rejected".formatted(ticket.getStatus()));
    }
    if (request == null || isBlank(request.reason())) {
      throw new PokaYokeViolationException("REJECTION_REASON_REQUIRED",
          "Cần ghi lý do từ chối yêu cầu");
    }
    workflow.decide(ticket.getWorkflowCaseId(), TicketStatus.REJECTED, user.actor(),
        request.reason().strip());
    return TicketResponse.of(ticket);
  }

  private TicketDetailResponse detailOf(Ticket ticket) {
    List<TicketComment> comments = user.isStaff()
        ? commentRepository.findByTicket_IdOrderByCreatedAtAscIdAsc(ticket.getId())
        : commentRepository.findByTicket_IdAndInternalFalseOrderByCreatedAtAscIdAsc(ticket.getId());
    return new TicketDetailResponse(TicketResponse.of(ticket),
        comments.stream().map(TicketService::toResponse).toList(),
        attachmentRepository.findByTicket_IdOrderByIdAsc(ticket.getId()).stream()
            .map(this::toResponse).toList(),
        ticket.getWorkflowCaseId() == null ? List.of()
            : timeline(workflow.events(ticket.getWorkflowCaseId())));
  }

  // Giving a ticket to someone logs the assignment and the acceptance back to back. They show as
  // one step: the person who took it, or the person who handed it over with the assignee kept in
  // the metadata.
  static List<WorkflowEventResponse> timeline(List<WorkflowEventResponse> events) {
    List<WorkflowEventResponse> steps = new ArrayList<>();
    for (int i = 0; i < events.size(); i++) {
      WorkflowEventResponse event = events.get(i);
      WorkflowEventResponse next = i + 1 < events.size() ? events.get(i + 1) : null;
      boolean handOver = "ASSIGNED".equals(event.action()) && next != null
          && LabWorkflowConfig.ACCEPTED.equals(next.action());
      if (!handOver) {
        steps.add(event);
        continue;
      }
      i++;
      if (Objects.equals(event.actorUserId(), next.actorUserId())) {
        steps.add(next);
      } else {
        steps.add(new WorkflowEventResponse(next.id(), ASSIGNED_BY, next.fromStatus(),
            next.toStatus(), next.actorUserId(), next.actorDisplayName(), null, next.occurredAt(),
            Map.of("assigneeName", event.actorDisplayName())));
      }
    }
    return steps;
  }

  // Someone else's ticket does not show up for a user who cannot see all of them.
  private Ticket findVisible(Long id) {
    Ticket ticket = ticketRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
    if (!user.isStaff() && !ticket.getRequesterId().equals(user.id())) {
      throw new ResourceNotFoundException("Ticket not found: " + id);
    }
    return ticket;
  }

  // What the technician asked for may come as photos or edits instead of a reply: either puts a
  // waiting ticket back to IN_PROGRESS and tells the technician. Returns whether it did.
  private boolean resumeAfterSupplement(Ticket ticket, String what) {
    if (!TicketStatus.WAITING_USER.equals(ticket.getStatus())) {
      return false;
    }
    workflow.decide(ticket.getWorkflowCaseId(), TicketStatus.IN_PROGRESS, user.actor(), null);
    tellAssignee(ticket, "Người gửi đã bổ sung thông tin",
        "Yêu cầu %s: người gửi " + what + " và chờ bạn xử lý tiếp.");
    return true;
  }

  private void tellAssignee(Ticket ticket, String title, String bodyFormat) {
    if (ticket.getAssigneeId() == null) {
      return;
    }
    notifier.tell(ticket.getAssigneeId(), LabNotificationTypes.TICKET_REPLIED, title,
        bodyFormat.formatted(ticket.getCode()), LabCaseTypes.TICKET_ENTITY, ticket.getId());
  }

  private Ticket findEditable(Long id) {
    Ticket ticket = findVisible(id);
    requireRequester(ticket);
    if (!OPEN_STATUSES.contains(ticket.getStatus())) {
      throw new InvalidStateException("Ticket %s is %s and can no longer be edited"
          .formatted(ticket.getCode(), ticket.getStatus()));
    }
    return ticket;
  }

  private Ticket findForUpdateAction(Long id) {
    Ticket ticket = findVisible(id);
    if (ticket.getWorkflowCaseId() == null) {
      throw new InvalidStateException("Ticket has no workflow case: " + id);
    }
    return ticket;
  }

  private static void requireStatus(Ticket ticket, String expected) {
    if (!expected.equals(ticket.getStatus())) {
      throw new InvalidStateException(
          "Ticket %s is %s, expected %s".formatted(ticket.getCode(), ticket.getStatus(), expected));
    }
  }

  private static void requireNotFinished(Ticket ticket) {
    if (List.of(TicketStatus.CLOSED, TicketStatus.REJECTED, TicketStatus.CANCELLED)
        .contains(ticket.getStatus())) {
      throw new InvalidStateException(
          "Ticket %s is already %s".formatted(ticket.getCode(), ticket.getStatus()));
    }
  }

  private void requireAssigneeOrAdmin(Ticket ticket) {
    if (!user.isAdmin() && !user.id().equals(ticket.getAssigneeId())) {
      throw new ActionNotAllowedException("Only the assigned technician can do this");
    }
  }

  private void requireRequester(Ticket ticket) {
    if (!user.id().equals(ticket.getRequesterId())) {
      throw new ActionNotAllowedException("Only the person who sent the ticket can do this");
    }
  }

  private TicketComment saveComment(Ticket ticket, String body, boolean internal) {
    return commentRepository.save(TicketComment.builder().ticket(ticket).authorId(user.id())
        .authorName(user.name()).staff(user.isStaff()).internal(internal).body(body).build());
  }

  private TicketAttachment store(Ticket ticket, MultipartFile file) {
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to read the attached file", e);
    }
    String name = file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename();
    String key = fileStore.store(TicketAttachmentProcessor.PURPOSE, bytes, name, user.id());
    return attachmentRepository.save(TicketAttachment.builder().ticket(ticket).objectKey(key)
        .filename(name.length() > 255 ? name.substring(0, 255) : name)
        .contentType(TicketAttachmentProcessor.contentTypeOfKey(key)).sizeBytes((long) bytes.length)
        .uploadedBy(user.id()).build());
  }

  private TicketAttachmentResponse toResponse(TicketAttachment attachment) {
    return new TicketAttachmentResponse(attachment.getId(), attachment.getFilename(),
        attachment.getContentType(), attachment.getSizeBytes(),
        objectStorage.presignedUrl(attachment.getObjectKey()), attachment.getCreatedAt());
  }

  private static TicketCommentResponse toResponse(TicketComment comment) {
    return new TicketCommentResponse(comment.getId(), comment.getAuthorId(),
        comment.getAuthorName(), comment.isStaff(), comment.isInternal(), comment.getBody(),
        comment.getCreatedAt());
  }

  private Room roomOf(Long roomId) {
    if (roomId == null) {
      return null;
    }
    return roomRepository.findById(roomId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown room: " + roomId));
  }

  // A free-text room only applies when no catalogue room is chosen.
  private static String noteOf(Long roomId, String note) {
    return roomId != null || isBlank(note) ? null : note.strip();
  }

  private String firstImageKey(Ticket ticket) {
    return attachmentRepository.findByTicket_IdOrderByIdAsc(ticket.getId()).stream()
        .filter(attachment -> attachment.getContentType().startsWith("image/"))
        .map(TicketAttachment::getObjectKey).findFirst().orElse(null);
  }

  private Device deviceOf(Long deviceId) {
    if (deviceId == null) {
      return null;
    }
    return deviceRepository.findById(deviceId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown device: " + deviceId));
  }

  private static Specification<Ticket> requesterIs(String requesterId) {
    return (root, q, cb) -> cb.equal(root.get("requesterId"), requesterId);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
