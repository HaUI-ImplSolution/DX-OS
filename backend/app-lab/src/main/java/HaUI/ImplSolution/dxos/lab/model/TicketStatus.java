// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.model;

// Statuses of the TICKET case type; the workflow stores them as plain strings.
public final class TicketStatus {

  public static final String NEW = "NEW";
  public static final String ASSIGNED = "ASSIGNED";
  public static final String IN_PROGRESS = "IN_PROGRESS";
  public static final String WAITING_USER = "WAITING_USER";
  public static final String RESOLVED = "RESOLVED";
  public static final String CLOSED = "CLOSED";
  public static final String REJECTED = "REJECTED";
  public static final String CANCELLED = "CANCELLED";

  private TicketStatus() {}
}
