// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab;

import HaUI.ImplSolution.dxos.core.api.workflow.CaseStatus;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseTransition;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseTypeDefinition;
import HaUI.ImplSolution.dxos.lab.model.BookingStatus;
import HaUI.ImplSolution.dxos.lab.model.LoanStatus;
import HaUI.ImplSolution.dxos.lab.model.TicketStatus;
import HaUI.ImplSolution.dxos.lab.service.LabCaseTypes;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The lifecycles of tickets, device loans and room bookings. A transition lists the roles that may
// take it; rules that depend on who owns the entity are checked by the services.
@Configuration
public class LabWorkflowConfig {

  public static final String ACCEPTED = "ACCEPTED";
  public static final String STARTED = "STARTED";
  public static final String WAITING_FOR_USER = "WAITING_FOR_USER";
  public static final String RESUMED = "RESUMED";
  public static final String RESOLVED = "RESOLVED";
  public static final String CONFIRMED = "CONFIRMED";
  public static final String REOPENED = "REOPENED";
  public static final String RETURNED = "RETURNED";

  private static final String S = LabSecurityPolicies.STUDENT;
  private static final String L = LabSecurityPolicies.LECTURER;
  private static final String T = LabSecurityPolicies.TECHNICIAN;
  private static final String A = LabSecurityPolicies.ADMIN;

  // NEW -> ASSIGNED -> IN_PROGRESS <-> WAITING_USER, IN_PROGRESS -> RESOLVED -> CLOSED or back to
  // IN_PROGRESS when reopened; NEW and ASSIGNED tickets can be REJECTED, NEW ones CANCELLED.
  @Bean
  public CaseTypeDefinition ticketCaseType() {
    return new CaseTypeDefinition(LabCaseTypes.TICKET, TicketStatus.NEW, List.of(
        CaseStatus.pending(TicketStatus.NEW), CaseStatus.pending(TicketStatus.ASSIGNED),
        CaseStatus.pending(TicketStatus.IN_PROGRESS), CaseStatus.waiting(TicketStatus.WAITING_USER),
        CaseStatus.waiting(TicketStatus.RESOLVED), CaseStatus.terminal(TicketStatus.CLOSED),
        CaseStatus.terminal(TicketStatus.REJECTED), CaseStatus.terminal(TicketStatus.CANCELLED)),
        List.of(
            new CaseTransition(TicketStatus.NEW, TicketStatus.ASSIGNED, ACCEPTED).withRoles(T, A),
            new CaseTransition(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS, STARTED)
                .withRoles(T, A),
            new CaseTransition(TicketStatus.IN_PROGRESS, TicketStatus.WAITING_USER,
                WAITING_FOR_USER).withRoles(T, A),
            new CaseTransition(TicketStatus.WAITING_USER, TicketStatus.IN_PROGRESS, RESUMED)
                .withRoles(S, L, T, A),
            new CaseTransition(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, RESOLVED)
                .withRoles(T, A),
            new CaseTransition(TicketStatus.RESOLVED, TicketStatus.CLOSED, CONFIRMED).withRoles(S,
                L, T, A),
            new CaseTransition(TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS, REOPENED)
                .withRoles(S, L, T, A),
            new CaseTransition(TicketStatus.NEW, TicketStatus.REJECTED).withRoles(A),
            new CaseTransition(TicketStatus.ASSIGNED, TicketStatus.REJECTED).withRoles(A),
            new CaseTransition(TicketStatus.NEW, TicketStatus.CANCELLED).withRoles(S, L, T, A)));
  }

  // PENDING -> APPROVED -> RETURNED; PENDING -> REJECTED or CANCELLED.
  @Bean
  public CaseTypeDefinition deviceLoanCaseType() {
    return new CaseTypeDefinition(LabCaseTypes.DEVICE_LOAN, LoanStatus.PENDING,
        List.of(CaseStatus.pending(LoanStatus.PENDING), CaseStatus.waiting(LoanStatus.APPROVED),
            CaseStatus.terminal(LoanStatus.RETURNED), CaseStatus.terminal(LoanStatus.REJECTED),
            CaseStatus.terminal(LoanStatus.CANCELLED)),
        List.of(new CaseTransition(LoanStatus.PENDING, LoanStatus.APPROVED).withRoles(L, T, A),
            new CaseTransition(LoanStatus.PENDING, LoanStatus.REJECTED).withRoles(L, T, A),
            new CaseTransition(LoanStatus.PENDING, LoanStatus.CANCELLED).withRoles(S, L, T, A),
            new CaseTransition(LoanStatus.APPROVED, LoanStatus.RETURNED, RETURNED).withRoles(T,
                A)));
  }

  // PENDING -> APPROVED or REJECTED; PENDING and APPROVED bookings can be CANCELLED.
  @Bean
  public CaseTypeDefinition roomBookingCaseType() {
    return new CaseTypeDefinition(LabCaseTypes.ROOM_BOOKING, BookingStatus.PENDING,
        List.of(CaseStatus.pending(BookingStatus.PENDING),
            CaseStatus.waiting(BookingStatus.APPROVED), CaseStatus.terminal(BookingStatus.REJECTED),
            CaseStatus.terminal(BookingStatus.CANCELLED)),
        List.of(new CaseTransition(BookingStatus.PENDING, BookingStatus.APPROVED).withRoles(T, A),
            new CaseTransition(BookingStatus.PENDING, BookingStatus.REJECTED).withRoles(T, A),
            new CaseTransition(BookingStatus.PENDING, BookingStatus.CANCELLED).withRoles(L, T, A),
            new CaseTransition(BookingStatus.APPROVED, BookingStatus.CANCELLED).withRoles(L, T,
                A)));
  }
}
