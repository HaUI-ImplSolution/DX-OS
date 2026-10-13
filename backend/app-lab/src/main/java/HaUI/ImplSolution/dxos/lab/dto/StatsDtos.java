// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Figures of the statistics page.
public final class StatsDtos {

  public record Count(String key, long count) {}

  public record TechnicianStat(String userId, String name, long openTickets, long closedTickets) {}

  public record RoomUsage(Long roomId, String roomCode, double bookedHours, double utilization) {}

  // Averages are in minutes and null when no ticket qualifies; rates are 0 to 1.
  public record LabStatsResponse(LocalDate from, LocalDate to, long ticketsCreated,
      List<Count> ticketsByStatus, List<Count> ticketsByCategory, Double slaAttainment,
      Double averageFirstResponseMinutes, Double averageResolutionMinutes, long overdueTickets,
      Double averageRating, long ratedTickets, List<TechnicianStat> technicians,
      List<RoomUsage> roomUsage, Map<String, Long> devicesByStatus, long overdueLoans) {}

  private StatsDtos() {}
}
