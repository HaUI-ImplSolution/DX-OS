// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import java.time.Instant;

// Facts the chat listener turns into channel posts once the transaction that caused them commits.
public final class LabEvents {

  public record TicketCreated(Long ticketId, String code, String title, String categoryName,
      String priority, String requesterName, Instant createdAt) {}

  public record TicketOverdue(Long ticketId, String code, String title, String priority,
      String assigneeName, Instant dueAt) {}

  private LabEvents() {}
}
