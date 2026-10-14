// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.lab.model.Ticket;
import HaUI.ImplSolution.dxos.lab.model.TicketPriority;
import HaUI.ImplSolution.dxos.lab.repository.TicketRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TicketSlaServiceTest {

  private static final Instant CREATED = Instant.parse("2026-10-14T01:00:00Z");

  private final TicketRepository tickets = mock(TicketRepository.class);
  private final LabNotifier notifier = mock(LabNotifier.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private Ticket ticket;

  @BeforeEach
  void setUp() {
    ticket = Ticket.builder().id(7L).code("TK-2026-0007").title("Máy chiếu hỏng")
        .priority(TicketPriority.NORMAL).assigneeId("tech-1").assigneeName("Tech One")
        .createdAt(CREATED).resolutionDueAt(CREATED.plus(Duration.ofHours(10))).build();
    when(tickets.findNewlyOverdue(any(), any())).thenReturn(List.of());
    when(tickets.findWarningCandidates(any(), any())).thenReturn(List.of());
  }

  private TicketSlaService serviceAt(Instant now) {
    return new TicketSlaService(tickets, notifier, events, Clock.fixed(now, ZoneOffset.UTC), 0.8);
  }

  @Test
  void ticketBeforeTheWarningShareIsLeftAlone() {
    when(tickets.findWarningCandidates(any(), any())).thenReturn(List.of(ticket));

    var result = serviceAt(CREATED.plus(Duration.ofHours(7))).check();

    assertThat(result.warned()).isZero();
    assertThat(ticket.getSlaWarnedAt()).isNull();
    verify(notifier, never()).tellOnce(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void ticketPastTheWarningShareIsWarnedOnceWithADedupKey() {
    when(tickets.findWarningCandidates(any(), any())).thenReturn(List.of(ticket));
    Instant now = CREATED.plus(Duration.ofHours(8)).plusSeconds(60);

    var result = serviceAt(now).check();

    assertThat(result.warned()).isEqualTo(1);
    assertThat(ticket.getSlaWarnedAt()).isEqualTo(now);
    verify(notifier).tellOnce(eq("ticket:7:sla-warning"), eq("tech-1"), any(), any(), any(), any(),
        eq(7L));
  }

  @Test
  void ticketPastItsDeadlineIsMarkedOverdueAndAnnounced() {
    when(tickets.findNewlyOverdue(any(), any())).thenReturn(List.of(ticket));
    Instant now = CREATED.plus(Duration.ofHours(11));

    var result = serviceAt(now).check();

    assertThat(result.overdue()).isEqualTo(1);
    assertThat(ticket.isOverdue()).isTrue();
    assertThat(ticket.getOverdueAt()).isEqualTo(now);
    verify(notifier).tellOnce(eq("ticket:7:overdue"), eq("tech-1"), any(), any(), any(), any(),
        eq(7L));
    verify(events).publishEvent(any(LabEvents.TicketOverdue.class));
  }
}
