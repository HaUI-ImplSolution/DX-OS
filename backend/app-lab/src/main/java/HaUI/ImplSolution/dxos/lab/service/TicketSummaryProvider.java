// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummary;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummaryProvider;
import HaUI.ImplSolution.dxos.lab.model.Ticket;
import HaUI.ImplSolution.dxos.lab.repository.TicketRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Describes tickets for the work queue.
@Component
@Transactional(readOnly = true)
public class TicketSummaryProvider implements CaseSummaryProvider {

  private final TicketRepository ticketRepository;

  public TicketSummaryProvider(TicketRepository ticketRepository) {
    this.ticketRepository = ticketRepository;
  }

  @Override
  public String entityType() {
    return LabCaseTypes.TICKET_ENTITY;
  }

  @Override
  public Map<Long, CaseSummary> summarize(Collection<Long> entityIds) {
    return ticketRepository.findByIdIn(entityIds).stream()
        .collect(Collectors.toMap(Ticket::getId, TicketSummaryProvider::summary));
  }

  @Override
  public List<Long> entityIdsOfWorkspace(Long workspaceId) {
    return ticketRepository.findByWorkspaceId(workspaceId).stream().map(Ticket::getId).toList();
  }

  private static CaseSummary summary(Ticket ticket) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("title", ticket.getTitle());
    details.put("category", ticket.getCategory().getName());
    details.put("priority", ticket.getPriority().name());
    details.put("room", ticket.getRoom() == null ? null : ticket.getRoom().getCode());
    details.put("device", ticket.getDevice() == null ? null : ticket.getDevice().getAssetCode());
    details.put("resolutionDueAt", ticket.getResolutionDueAt());
    details.put("overdue", ticket.isOverdue());
    return new CaseSummary(ticket.getCode(), ticket.getWorkspaceId(), ticket.getRequesterName(),
        details);
  }
}
