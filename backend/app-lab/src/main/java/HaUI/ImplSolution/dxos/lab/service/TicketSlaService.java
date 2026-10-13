// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.lab.model.Ticket;
import HaUI.ImplSolution.dxos.lab.repository.TicketRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Watches the resolution deadline of open tickets: a warning once the configured share of the time
// is used up and a mark when the deadline passes. Each message is written once per ticket.
@Service
public class TicketSlaService {

  public record Result(int warned, int overdue) {}

  private final TicketRepository ticketRepository;
  private final LabNotifier notifier;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final double warningRatio;

  public TicketSlaService(TicketRepository ticketRepository, LabNotifier notifier,
      ApplicationEventPublisher events, Clock clock,
      @Value("${lab.sla.warning-ratio:0.8}") double warningRatio) {
    this.ticketRepository = ticketRepository;
    this.notifier = notifier;
    this.events = events;
    this.clock = clock;
    this.warningRatio = warningRatio;
  }

  @Transactional
  public Result check() {
    Instant now = clock.instant();
    int overdue = 0;
    for (Ticket ticket : ticketRepository.findNewlyOverdue(TicketService.OPEN_STATUSES, now)) {
      ticket.setOverdue(true);
      ticket.setOverdueAt(now);
      ticketRepository.save(ticket);
      notifier.tellOnce("ticket:%d:overdue".formatted(ticket.getId()), ticket.getAssigneeId(),
          LabNotificationTypes.TICKET_OVERDUE, "Yêu cầu quá hạn xử lý",
          "Yêu cầu %s: %s đã quá hạn xử lý.".formatted(ticket.getCode(), ticket.getTitle()),
          LabCaseTypes.TICKET_ENTITY, ticket.getId());
      events.publishEvent(
          new LabEvents.TicketOverdue(ticket.getId(), ticket.getCode(), ticket.getTitle(),
              ticket.getPriority().name(), ticket.getAssigneeName(), ticket.getResolutionDueAt()));
      overdue++;
    }
    int warned = 0;
    for (Ticket ticket : ticketRepository.findWarningCandidates(TicketService.OPEN_STATUSES, now)) {
      if (now.isBefore(warningAt(ticket))) {
        continue;
      }
      ticket.setSlaWarnedAt(now);
      ticketRepository.save(ticket);
      notifier.tellOnce("ticket:%d:sla-warning".formatted(ticket.getId()), ticket.getAssigneeId(),
          LabNotificationTypes.TICKET_SLA_WARNING, "Yêu cầu sắp quá hạn",
          "Yêu cầu %s: %s đã dùng hết %d%% thời gian xử lý.".formatted(ticket.getCode(),
              ticket.getTitle(), Math.round(warningRatio * 100)),
          LabCaseTypes.TICKET_ENTITY, ticket.getId());
      warned++;
    }
    return new Result(warned, overdue);
  }

  private Instant warningAt(Ticket ticket) {
    long total = Duration.between(ticket.getCreatedAt(), ticket.getResolutionDueAt()).toMillis();
    return ticket.getCreatedAt().plusMillis(Math.round(total * warningRatio));
  }
}
