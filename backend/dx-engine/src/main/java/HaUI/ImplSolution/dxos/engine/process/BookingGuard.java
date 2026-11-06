// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.engine.apply.Sql;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.model.Booking;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.ProcessFamily.State;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Keeps the bookings of a resource within its capacity. The resource row is locked until the
// transaction ends, so two requests for the same time cannot both pass.
@Component
public class BookingGuard {

  private final JdbcTemplate jdbc;

  public BookingGuard(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // Checks a record about to be created (selfId null) or changed. Values holds the fields being
  // written; the rest are read from the record.
  public void check(Model model, Entity entity, Map<String, Object> values, Long selfId) {
    Booking booking = entity.process() == null ? null : entity.process().booking();
    if (booking == null) {
      return;
    }
    if (selfId != null && !values.containsKey(booking.resource())
        && !values.containsKey(booking.starts()) && !values.containsKey(booking.ends())) {
      return;
    }
    Map<String, Object> effective = new LinkedHashMap<>();
    if (selfId != null) {
      effective.putAll(jdbc.queryForMap(
          "SELECT " + Sql.ident(booking.resource()) + ", " + Sql.ident(booking.starts()) + ", "
              + Sql.ident(booking.ends()) + " FROM " + Sql.table(model, entity) + " WHERE id = ?",
          selfId));
    }
    effective.putAll(values);
    Object resource = effective.get(booking.resource());
    OffsetDateTime starts = time(effective.get(booking.starts()));
    OffsetDateTime ends = time(effective.get(booking.ends()));
    if (resource == null || starts == null || ends == null) {
      return;
    }
    if (!ends.isAfter(starts)) {
      throw new ModelValidationException(booking.ends(), "must be after " + booking.starts());
    }
    Entity target = model.entity(entity.field(booking.resource()).orElseThrow().to()).orElseThrow();
    jdbc.queryForList("SELECT id FROM " + Sql.table(model, target) + " WHERE id = ? FOR UPDATE",
        resource);
    List<String> open =
        entity.process().definition().states().stream().filter(state -> !state.terminal())
            .map(State::name).map(Sql::literal).collect(Collectors.toList());
    List<Map<String, Object>> overlapping = jdbc.queryForList(
        "SELECT " + Sql.ident(booking.starts()) + " AS starts, " + Sql.ident(booking.ends())
            + " AS ends FROM " + Sql.table(model, entity) + " WHERE "
            + Sql.ident(booking.resource()) + " = ? AND " + Sql.ident(booking.starts())
            + " < ? AND " + Sql.ident(booking.ends()) + " > ? AND status IN ("
            + String.join(", ", open) + ") AND id <> ?",
        resource, ends, starts, selfId == null ? -1L : selfId);
    if (busiest(overlapping, starts.toInstant(), ends.toInstant()) >= booking.capacity()) {
      throw new InvalidStateException("The resource is already booked for this time", "SLOT_TAKEN");
    }
  }

  // The most bookings running at one moment between starts and ends. A booking ending when another
  // starts does not overlap it.
  private static int busiest(List<Map<String, Object>> bookings, Instant starts, Instant ends) {
    List<Edge> edges = new ArrayList<>();
    for (Map<String, Object> row : bookings) {
      Instant from = time(row.get("starts")).toInstant();
      Instant to = time(row.get("ends")).toInstant();
      edges.add(new Edge(from.isBefore(starts) ? starts : from, 1));
      edges.add(new Edge(to.isAfter(ends) ? ends : to, -1));
    }
    edges.sort(Comparator.comparing(Edge::at).thenComparingInt(Edge::change));
    int running = 0;
    int most = 0;
    for (Edge edge : edges) {
      running += edge.change();
      most = Math.max(most, running);
    }
    return most;
  }

  private record Edge(Instant at, int change) {}

  private static OffsetDateTime time(Object value) {
    if (value instanceof OffsetDateTime time) {
      return time;
    }
    if (value instanceof java.sql.Timestamp stamp) {
      return stamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
    return null;
  }
}
