// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.controller;

import HaUI.ImplSolution.dxos.core.api.audit.AuditEvent;
import HaUI.ImplSolution.dxos.core.api.audit.AuditRecorder;
import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
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
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TicketSuggestRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TicketSuggestionResponse;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TicketUpdateRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.WaitRequest;
import HaUI.ImplSolution.dxos.lab.model.TicketPriority;
import HaUI.ImplSolution.dxos.lab.service.LabUser;
import HaUI.ImplSolution.dxos.lab.service.TicketFilter;
import HaUI.ImplSolution.dxos.lab.service.TicketService;
import HaUI.ImplSolution.dxos.lab.service.TicketSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Ticket", description = "IT support tickets - Process")
public class TicketController {

  private final TicketService ticketService;
  private final TicketSuggestionService suggestionService;
  private final LabUser user;
  private final AuditRecorder auditRecorder;

  public TicketController(TicketService ticketService, TicketSuggestionService suggestionService,
      LabUser user, AuditRecorder auditRecorder) {
    this.ticketService = ticketService;
    this.suggestionService = suggestionService;
    this.user = user;
    this.auditRecorder = auditRecorder;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Report an incident",
      description = "Any signed-in user. Multipart: the part `ticket` holds the JSON and `files`"
          + " up to 5 JPEG, PNG or PDF files of at most 5 MiB (400 otherwise). The code is"
          + " TK-<year>-<number>; the response and resolution deadlines come from the SLA policy"
          + " of the priority, which defaults to the category's. With an Idempotency-Key, a"
          + " repeated request returns the existing ticket.")
  public ApiResponse<TicketDetailResponse> create(
      @Valid @RequestPart("ticket") TicketCreateRequest request,
      @RequestPart(value = "files", required = false) List<MultipartFile> files,
      @Parameter(description = "Client-generated UUID that makes retries of this request safe")
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    UUID clientRequestId = idempotencyKey == null ? null : UUID.fromString(idempotencyKey);
    return ApiResponse.success("Ticket created",
        ticketService.create(request, files, clientRequestId));
  }

  @PostMapping("/suggest")
  @Operation(summary = "Suggest a category and priority and show related knowledge",
      description = "Any signed-in user. Rule based: the response lists the matched keywords and"
          + " the reasons, and up to 3 quoted knowledge passages with their sources. The user"
          + " decides. knowledgeAvailable is false when the AI service could not be reached; the"
          + " rule suggestion is still returned.")
  public ApiResponse<TicketSuggestionResponse> suggest(
      @Valid @RequestBody TicketSuggestRequest request) {
    return ApiResponse.success(suggestionService.suggest(request));
  }

  @GetMapping("/technicians")
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @Operation(summary = "Technicians who have handled tickets, with their open tickets",
      description = "TECHNICIAN or ADMIN only. A technician appears after taking a first ticket.")
  public ApiResponse<List<TechnicianLoadResponse>> technicians() {
    return ApiResponse.success(ticketService.technicianLoad());
  }

  @GetMapping
  @Operation(summary = "List tickets, newest first",
      description = "A user without TECHNICIAN or ADMIN sees their own tickets only. Filters:"
          + " status, categoryCode, priority, assignedTo (a user id, or `me`), unassigned=true,"
          + " overdue and requesterId (staff only). size is capped at 100.")
  public ApiResponse<PageResponse<TicketResponse>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String categoryCode,
      @RequestParam(required = false) TicketPriority priority,
      @RequestParam(required = false) String assignedTo,
      @RequestParam(defaultValue = "false") boolean unassigned,
      @RequestParam(required = false) Boolean overdue,
      @RequestParam(required = false) String requesterId,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    String assignee = "me".equals(assignedTo) ? user.id() : assignedTo;
    return ApiResponse.success(ticketService.list(new TicketFilter(status, categoryCode, priority,
        assignee, unassigned, overdue, requesterId), page, size));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Ticket with its comments, attachments and timeline",
      description = "404 for a ticket of someone else unless TECHNICIAN or ADMIN. Internal notes"
          + " are shown to staff only; attachment links expire.")
  public ApiResponse<TicketDetailResponse> detail(@PathVariable Long id) {
    return ApiResponse.success(ticketService.detail(id));
  }

  @GetMapping("/{id}/comments")
  @Operation(summary = "Comments of a ticket, oldest first",
      description = "Same visibility rules as the ticket itself.")
  public ApiResponse<List<TicketCommentResponse>> comments(@PathVariable Long id) {
    return ApiResponse.success(ticketService.detail(id).comments());
  }

  @PostMapping("/{id}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Reply on a ticket",
      description = "The requester and staff. internal=true is for staff only (403). 409 once the"
          + " ticket is closed, rejected or cancelled. A reply of the requester on a ticket in"
          + " WAITING_USER puts it back to IN_PROGRESS. The first public reply of staff is the"
          + " first response.")
  public ApiResponse<TicketCommentResponse> addComment(@PathVariable Long id,
      @Valid @RequestBody TicketCommentRequest request) {
    return ApiResponse.success("Comment added", ticketService.addComment(id, request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Edit the content of a ticket",
      description = "The requester only (403 otherwise). 409 once the ticket is resolved, closed,"
          + " rejected or cancelled. Changes title, description, room (a catalogue room, or"
          + " roomNote when none is chosen) and device, and adds a public reply that says so.")
  public ApiResponse<TicketResponse> update(@PathVariable Long id,
      @Valid @RequestBody TicketUpdateRequest request) {
    return ApiResponse.success("Ticket updated", ticketService.update(id, request));
  }

  @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Attach an image or PDF of at most 5 MiB",
      description = "The requester only (403 otherwise), while the ticket is open (409 after"
          + " that). 400 for another type, a larger file or more than 5 attachments.")
  public ApiResponse<TicketAttachmentResponse> addAttachment(@PathVariable Long id,
      @RequestPart("file") MultipartFile file) {
    return ApiResponse.success("Attachment added", ticketService.addAttachment(id, file));
  }

  @DeleteMapping("/{id}/attachments/{attachmentId}")
  @Operation(summary = "Remove an attachment",
      description = "The requester only (403 otherwise), while the ticket is open (409 after"
          + " that). 404 for an attachment of another ticket.")
  public ApiResponse<Void> removeAttachment(@PathVariable Long id,
      @PathVariable Long attachmentId) {
    ticketService.removeAttachment(id, attachmentId);
    auditRecorder.record(AuditEvent.success("TICKET_ATTACHMENT_REMOVED", "TICKET",
        String.valueOf(id), java.util.Map.of("attachmentId", attachmentId)));
    return ApiResponse.success("Attachment removed", null);
  }

  @PostMapping("/{id}/assign")
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @Operation(summary = "Take a ticket, or hand it to a technician",
      description = "TECHNICIAN or ADMIN. Without a body the caller takes a NEW ticket (409 when"
          + " someone else got it first). With assigneeId and assigneeName an ADMIN gives a ticket"
          + " in NEW, ASSIGNED or IN_PROGRESS to that technician; a TECHNICIAN sending a body gets"
          + " 403.")
  public ApiResponse<TicketResponse> assign(@PathVariable Long id,
      @Valid @RequestBody(required = false) AssignRequest request) {
    if (request == null) {
      return ApiResponse.success("Ticket taken", ticketService.accept(id));
    }
    if (!user.isAdmin()) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Only an administrator assigns a ticket to someone else");
    }
    return ApiResponse.success("Ticket assigned", ticketService.assign(id, request));
  }

  @PostMapping("/{id}/start")
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @Operation(summary = "Start working on an assigned ticket",
      description = "The assigned technician or an ADMIN. 409 unless the ticket is ASSIGNED."
          + " Records the first response when there was none.")
  public ApiResponse<TicketResponse> start(@PathVariable Long id) {
    return ApiResponse.success("Ticket started", ticketService.start(id));
  }

  @PostMapping("/{id}/wait")
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @Operation(summary = "Ask the requester for more information",
      description = "The assigned technician or an ADMIN. message is posted as a public reply"
          + " (422 WAIT_MESSAGE_REQUIRED without it). 409 unless the ticket is IN_PROGRESS.")
  public ApiResponse<TicketResponse> waitForUser(@PathVariable Long id,
      @Valid @RequestBody(required = false) WaitRequest request) {
    return ApiResponse.success("Waiting for the requester", ticketService.waitForUser(id, request));
  }

  @PostMapping("/{id}/resolve")
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @Operation(summary = "Finish a ticket",
      description = "The assigned technician or an ADMIN. 422 RESOLUTION_NOTE_REQUIRED or"
          + " ROOT_CAUSE_REQUIRED without a note and a root cause. 409 unless IN_PROGRESS.")
  public ApiResponse<TicketResponse> resolve(@PathVariable Long id,
      @Valid @RequestBody(required = false) ResolveRequest request) {
    return ApiResponse.success("Ticket resolved", ticketService.resolve(id, request));
  }

  @PostMapping("/{id}/confirm")
  @Operation(summary = "Accept the result and rate it",
      description = "The requester only (403 otherwise). rating 1 to 5 is required (422"
          + " RATING_REQUIRED). 409 unless the ticket is RESOLVED. The ticket becomes CLOSED.")
  public ApiResponse<TicketResponse> confirm(@PathVariable Long id,
      @Valid @RequestBody(required = false) ConfirmRequest request) {
    return ApiResponse.success("Ticket closed", ticketService.confirm(id, request));
  }

  @PostMapping("/{id}/reopen")
  @Operation(summary = "Ask for more work on a resolved ticket",
      description = "The requester only (403 otherwise). 409 unless the ticket is RESOLVED."
          + " 422 TICKET_REOPEN_LIMIT after 3 reopenings.")
  public ApiResponse<TicketResponse> reopen(@PathVariable Long id,
      @Valid @RequestBody(required = false) ReopenRequest request) {
    return ApiResponse.success("Ticket reopened", ticketService.reopen(id, request));
  }

  @PostMapping("/{id}/cancel")
  @Operation(summary = "Withdraw a ticket nobody has taken",
      description = "The requester or an ADMIN. 409 unless the ticket is NEW.")
  public ApiResponse<TicketResponse> cancel(@PathVariable Long id) {
    return ApiResponse.success("Ticket cancelled", ticketService.cancel(id));
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Refuse a ticket that is not a valid request",
      description = "ADMIN only. reason is required (422 REJECTION_REASON_REQUIRED). 409 unless"
          + " the ticket is NEW or ASSIGNED.")
  public ApiResponse<TicketResponse> reject(@PathVariable Long id,
      @Valid @RequestBody(required = false) RejectRequest request) {
    return ApiResponse.success("Ticket rejected", ticketService.reject(id, request));
  }
}
