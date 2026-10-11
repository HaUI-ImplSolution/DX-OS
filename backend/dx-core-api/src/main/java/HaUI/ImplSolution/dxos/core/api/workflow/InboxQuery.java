// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

import java.time.LocalDate;

// Filters of the approval inbox; null fields are not applied. from and to are inclusive days.
// assignedTo: a user id. unassigned: only cases nobody has taken.
public record InboxQuery(String caseType,String status,String workspaceCode,String assignedTo,boolean unassigned,LocalDate from,LocalDate to){}
