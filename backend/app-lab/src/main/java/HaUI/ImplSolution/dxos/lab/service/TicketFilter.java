// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.lab.model.TicketPriority;

// Filters of the ticket list; null fields are not applied. Non-staff users are always limited to
// their own tickets, whatever the filter says.
public record TicketFilter(String status,String categoryCode,TicketPriority priority,String assigneeId,boolean unassigned,Boolean overdue,String requesterId){}
