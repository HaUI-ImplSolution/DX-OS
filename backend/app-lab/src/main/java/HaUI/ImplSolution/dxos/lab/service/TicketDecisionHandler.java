// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseView;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowDecisionHandler;
import HaUI.ImplSolution.dxos.lab.model.Ticket;
import HaUI.ImplSolution.dxos.lab.model.TicketStatus;
import HaUI.ImplSolution.dxos.lab.repository.TicketRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

// Mirrors every step of a TICKET case onto the ticket: status, assignee, milestones and the
// message to the requester. Runs in the transaction that records the step.
@Component
public class TicketDecisionHandler implements WorkflowDecisionHandler {

  private final TicketRepository ticketRepository;
  private final LabNotifier notifier;
  private final Clock clock;

  public TicketDecisionHandler(TicketRepository ticketRepository, LabNotifier notifier,
      Clock clock) {
    this.ticketRepository = ticketRepository;
    this.notifier = notifier;
    this.clock = clock;
  }

  @Override
  public String caseType() {
    return LabCaseTypes.TICKET;
  }

  @Override
  public void onDecision(CaseView workflowCase, String target, String reason) {
    Ticket ticket = ticketRepository.findById(workflowCase.entityId()).orElseThrow(
        () -> new ResourceNotFoundException("Ticket not found: " + workflowCase.entityId()));
    String previous = ticket.getStatus();
    Instant now = clock.instant();
    ticket.setStatus(target);
    ticket.setAssigneeId(workflowCase.assignedTo());
    ticket.setAssigneeName(workflowCase.assignedToName());
    switch (target) {
      case TicketStatus.IN_PROGRESS -> {
        if (TicketStatus.RESOLVED.equals(previous)) {
          ticket.setReopenCount(ticket.getReopenCount() + 1);
          ticket.setResolvedAt(null);
        } else if (ticket.getFirstRespondedAt() == null) {
          ticket.setFirstRespondedAt(now);
        }
      }
      case TicketStatus.RESOLVED -> ticket.setResolvedAt(now);
      case TicketStatus.CLOSED -> ticket.setClosedAt(now);
      default -> {
      }
    }
    ticketRepository.save(ticket);
    tellRequester(ticket, target, reason);
  }

  private void tellRequester(Ticket ticket, String target, String reason) {
    String code = ticket.getCode();
    switch (target) {
      case TicketStatus.ASSIGNED -> notifier.tell(ticket.getRequesterId(),
          LabNotificationTypes.TICKET_ASSIGNED, "Yêu cầu được tiếp nhận",
          "Yêu cầu %s đã được %s tiếp nhận.".formatted(code, ticket.getAssigneeName()),
          LabCaseTypes.TICKET_ENTITY, ticket.getId());
      case TicketStatus.WAITING_USER ->
        notifier.tell(ticket.getRequesterId(), LabNotificationTypes.TICKET_WAITING_USER,
            "Cần bạn bổ sung thông tin", "Yêu cầu %s đang chờ phản hồi của bạn.".formatted(code),
            LabCaseTypes.TICKET_ENTITY, ticket.getId());
      case TicketStatus.RESOLVED -> notifier.tell(ticket.getRequesterId(),
          LabNotificationTypes.TICKET_RESOLVED, "Yêu cầu đã xử lý xong",
          "Yêu cầu %s đã xử lý xong, hãy xác nhận và đánh giá.".formatted(code),
          LabCaseTypes.TICKET_ENTITY, ticket.getId());
      case TicketStatus.REJECTED ->
        notifier.tell(ticket.getRequesterId(), LabNotificationTypes.TICKET_RESOLVED,
            "Yêu cầu bị từ chối", "Yêu cầu %s bị từ chối. Lý do: %s".formatted(code, reason),
            LabCaseTypes.TICKET_ENTITY, ticket.getId());
      default -> {
      }
    }
  }
}
