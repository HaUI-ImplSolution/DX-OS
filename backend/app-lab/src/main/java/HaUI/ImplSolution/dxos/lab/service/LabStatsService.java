// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import HaUI.ImplSolution.dxos.lab.dto.StatsDtos.Count;
import HaUI.ImplSolution.dxos.lab.dto.StatsDtos.LabStatsResponse;
import HaUI.ImplSolution.dxos.lab.dto.StatsDtos.RoomUsage;
import HaUI.ImplSolution.dxos.lab.dto.StatsDtos.TechnicianStat;
import HaUI.ImplSolution.dxos.lab.model.LoanStatus;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Figures for the statistics page, computed from the Lab tables for a range of days. Averages are
// in minutes, rates between 0 and 1.
@Service
@Transactional(readOnly = true)
public class LabStatsService {

  private static final int DEFAULT_RANGE_DAYS = 30;
  private static final int MAX_RANGE_DAYS = 366;
  private static final String OPEN = "('NEW', 'ASSIGNED', 'IN_PROGRESS', 'WAITING_USER')";

  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final double roomOpenHoursPerDay;

  public LabStatsService(JdbcTemplate jdbc, Clock clock,
      @Value("${lab.stats.room-open-hours-per-day:10}") double roomOpenHoursPerDay) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.roomOpenHoursPerDay = roomOpenHoursPerDay;
  }

  public LabStatsResponse summary(LocalDate from, LocalDate to) {
    LocalDate end = to != null ? to : LocalDate.now(clock);
    LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS - 1L);
    if (end.isBefore(start)) {
      throw new IllegalArgumentException("from must not be after to");
    }
    long days = ChronoUnit.DAYS.between(start, end) + 1;
    if (days > MAX_RANGE_DAYS) {
      throw new IllegalArgumentException(
          "The range may span at most %d days".formatted(MAX_RANGE_DAYS));
    }
    OffsetDateTime rangeStart = start.atStartOfDay(BusinessTime.ZONE).toOffsetDateTime();
    OffsetDateTime rangeEnd = end.plusDays(1).atStartOfDay(BusinessTime.ZONE).toOffsetDateTime();

    Long created =
        jdbc.queryForObject("SELECT count(*) FROM tickets WHERE created_at >= ? AND created_at < ?",
            Long.class, rangeStart, rangeEnd);
    List<Count> byStatus = jdbc.query("""
        SELECT status AS key, count(*) AS total FROM tickets
        WHERE created_at >= ? AND created_at < ? GROUP BY status ORDER BY status
        """, (rs, i) -> new Count(rs.getString("key"), rs.getLong("total")), rangeStart, rangeEnd);
    List<Count> byCategory = jdbc.query("""
        SELECT c.code AS key, count(*) AS total FROM tickets t
        JOIN ticket_categories c ON c.id = t.category_id
        WHERE t.created_at >= ? AND t.created_at < ? GROUP BY c.code ORDER BY total DESC, c.code
        """, (rs, i) -> new Count(rs.getString("key"), rs.getLong("total")), rangeStart, rangeEnd);

    // Tickets with a settled outcome: resolved, or already past the deadline.
    Map<String, Object> sla = jdbc.queryForMap("""
        SELECT count(*) FILTER (WHERE resolved_at IS NOT NULL AND resolved_at <= resolution_due_at)
                   AS in_time,
               count(*) FILTER (WHERE resolved_at IS NOT NULL OR overdue) AS settled,
               avg(extract(epoch FROM (first_responded_at - created_at)) / 60)
                   FILTER (WHERE first_responded_at IS NOT NULL) AS avg_response,
               avg(extract(epoch FROM (resolved_at - created_at)) / 60)
                   FILTER (WHERE resolved_at IS NOT NULL) AS avg_resolution,
               count(*) FILTER (WHERE overdue) AS overdue,
               avg(rating) FILTER (WHERE rating IS NOT NULL) AS avg_rating,
               count(*) FILTER (WHERE rating IS NOT NULL) AS rated
        FROM tickets WHERE created_at >= ? AND created_at < ?
        """, rangeStart, rangeEnd);
    long settled = ((Number) sla.get("settled")).longValue();
    Double attainment = settled == 0 ? null : ((Number) sla.get("in_time")).doubleValue() / settled;

    List<TechnicianStat> technicians = jdbc.query(
        """
            SELECT assignee_id, max(assignee_name) AS name,
                   count(*) FILTER (WHERE status IN %s) AS open_tickets,
                   count(*) FILTER (WHERE status = 'CLOSED' AND closed_at >= ? AND closed_at < ?)
                       AS closed_tickets
            FROM tickets WHERE assignee_id IS NOT NULL
            GROUP BY assignee_id ORDER BY max(assignee_name)
            """.formatted(OPEN), (rs, i) -> new TechnicianStat(rs.getString("assignee_id"),
            rs.getString("name"), rs.getLong("open_tickets"), rs.getLong("closed_tickets")),
        rangeStart, rangeEnd);

    double openHours = days * roomOpenHoursPerDay;
    List<RoomUsage> rooms = jdbc.query("""
        SELECT r.id, r.code,
               coalesce(sum(extract(epoch FROM (least(b.ends_at, ?) - greatest(b.starts_at, ?)))
                            / 3600), 0) AS booked_hours
        FROM rooms r
        LEFT JOIN room_bookings b ON b.room_id = r.id AND b.status = 'APPROVED'
             AND b.starts_at < ? AND b.ends_at > ?
        GROUP BY r.id, r.code ORDER BY r.code
        """, (rs, i) -> {
      double booked = rs.getDouble("booked_hours");
      return new RoomUsage(rs.getLong("id"), rs.getString("code"), round(booked),
          openHours <= 0 ? 0 : round(Math.min(1.0, booked / openHours)));
    }, rangeEnd, rangeStart, rangeEnd, rangeStart);

    Map<String, Long> devices = new LinkedHashMap<>();
    jdbc.query(
        "SELECT status AS key, count(*) AS total FROM devices GROUP BY status ORDER BY status",
        (rs, i) -> new Count(rs.getString("key"), rs.getLong("total")))
        .forEach(row -> devices.put(row.key(), row.count()));
    Long overdueLoans =
        jdbc.queryForObject("SELECT count(*) FROM device_loans WHERE status = ? AND due_date < ?",
            Long.class, LoanStatus.APPROVED, java.sql.Date.valueOf(LocalDate.now(clock)));

    return new LabStatsResponse(start, end, created == null ? 0 : created, byStatus, byCategory,
        attainment, minutes(sla.get("avg_response")), minutes(sla.get("avg_resolution")),
        ((Number) sla.get("overdue")).longValue(), rating(sla.get("avg_rating")),
        ((Number) sla.get("rated")).longValue(), technicians, rooms, devices,
        overdueLoans == null ? 0 : overdueLoans);
  }

  private static Double minutes(Object value) {
    return value == null ? null : round(((Number) value).doubleValue());
  }

  private static Double rating(Object value) {
    return value == null ? null : round(((Number) value).doubleValue());
  }

  private static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
