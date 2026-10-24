// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.query;

import HaUI.ImplSolution.dxos.monitor.query.Views.AdminAction;
import HaUI.ImplSolution.dxos.monitor.query.Views.Bucket;
import HaUI.ImplSolution.dxos.monitor.query.Views.ErrorPath;
import HaUI.ImplSolution.dxos.monitor.query.Views.Health;
import HaUI.ImplSolution.dxos.monitor.query.Views.ServiceState;
import HaUI.ImplSolution.dxos.monitor.query.Views.StateChange;
import HaUI.ImplSolution.dxos.monitor.query.Views.Summary;
import HaUI.ImplSolution.dxos.monitor.query.Views.Suspect;
import HaUI.ImplSolution.dxos.monitor.query.Views.Suspects;
import HaUI.ImplSolution.dxos.monitor.query.Views.Timeline;
import HaUI.ImplSolution.dxos.monitor.query.Views.TraceEntry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// Read-only questions over the audit log, the gateway events, the service states and the sign-in
// events. Every value reaches SQL as a bind parameter; the only text placed in a statement is the
// bucket interval, a constant of Window.
@Service
public class MonitorQueryService {

  private static final int SUSPECT_LIMIT = 10;
  private static final int PATH_LIMIT = 10;
  private static final int TRACE_LIMIT = 200;
  private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final Pattern ADDRESS =
      Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}|(?=.{2,45}$)[0-9A-Fa-f:]*:[0-9A-Fa-f:.]*");
  private static final String DOWN = "DOWN";

  private final JdbcTemplate jdbc;
  private final AuthEventReader authEvents;

  public MonitorQueryService(JdbcTemplate jdbc, AuthEventReader authEvents) {
    this.jdbc = jdbc;
    this.authEvents = authEvents;
  }

  public Summary summary(Window window) {
    Timestamp from = Timestamp.from(window.start());
    long denied = count(
        "SELECT count(*) FROM public.audit_log " + "WHERE outcome = 'DENIED' AND occurred_at >= ?",
        from);
    long backendErrors = count("SELECT count(*) FROM public.audit_log "
        + "WHERE action = 'SERVER_ERROR' AND occurred_at >= ?", from);
    long serverErrors = gatewayCount("kind IN ('SERVER_ERROR', 'UPSTREAM_DOWN')", from);
    long rateLimited = gatewayCount("kind = 'RATE_LIMITED'", from);
    Long failedLogins = authEvents.countFailed(from.toInstant()).orElse(null);
    long down = health().services().stream().filter(s -> DOWN.equals(s.state())).count();
    return new Summary(window.code(), denied, serverErrors, backendErrors, rateLimited,
        failedLogins, down);
  }

  public Timeline timeline(Window window) {
    return timeline(window.code(), window.start(), Instant.now());
  }

  // A span the user zoomed into.
  public Timeline timeline(Instant from, Instant to) {
    if (from == null || to == null || !to.isAfter(from.plusSeconds(60))
        || Duration.between(from, to).compareTo(Duration.ofDays(8)) > 0) {
      throw new IllegalArgumentException("from and to must span between 1 minute and 8 days");
    }
    return timeline("custom", from, to);
  }

  private Timeline timeline(String label, Instant from, Instant to) {
    Bucketing bucketing = Bucketing.forSpan(Duration.between(from, to));
    String bucket =
        "date_bin(interval '" + bucketing.interval() + "', occurred_at, " + Bucketing.ORIGIN + ")";
    Timestamp start = Timestamp.from(from);
    Timestamp end = Timestamp.from(to);
    Map<Instant, Counts> buckets = new TreeMap<>();
    for (Instant cursor = bucketing.start(from); cursor.isBefore(to); cursor =
        cursor.plusSeconds(bucketing.seconds())) {
      buckets.put(cursor, new Counts());
    }
    jdbc.query("SELECT " + bucket + " AS bucket, count(*) AS total, min(occurred_at) AS first_at, "
        + "max(occurred_at) AS last_at FROM public.audit_log WHERE outcome = 'DENIED' "
        + "AND occurred_at >= ? AND occurred_at < ? GROUP BY bucket", rs -> {
          Counts counts = buckets.get(rs.getTimestamp("bucket").toInstant());
          if (counts != null) {
            counts.denied += rs.getLong("total");
            counts.seen(rs.getTimestamp("first_at").toInstant(),
                rs.getTimestamp("last_at").toInstant());
          }
        }, start, end);
    jdbc.query("SELECT " + bucket + " AS bucket, kind, count(*) + sum(suppressed) AS total, "
        + "min(occurred_at) AS first_at, max(occurred_at) AS last_at FROM gateway_event "
        + "WHERE occurred_at >= ? AND occurred_at < ? GROUP BY bucket, kind", rs -> {
          Counts counts = buckets.get(rs.getTimestamp("bucket").toInstant());
          if (counts != null) {
            counts.add(rs.getString("kind"), rs.getLong("total"),
                rs.getTimestamp("first_at").toInstant(), rs.getTimestamp("last_at").toInstant());
          }
        }, start, end);
    Map<Instant, AuthEventReader.BucketCount> failed =
        authEvents.failedPerBucket(from, to, bucketing.interval()).orElse(null);
    List<Bucket> result = new ArrayList<>();
    buckets.forEach((at, counts) -> {
      AuthEventReader.BucketCount logins = failed == null ? null : failed.get(at);
      result.add(new Bucket(at, counts.denied, counts.rateLimited, counts.authRejected,
          counts.notFound, counts.serverErrors, counts.slow,
          failed == null ? null : logins == null ? 0L : logins.count(), counts.first, counts.last,
          logins == null ? null : logins.first(), logins == null ? null : logins.last()));
    });
    return new Timeline(label, from, to, bucketing.seconds(), result);
  }

  public Suspects suspects(Window window) {
    Timestamp from = Timestamp.from(window.start());
    Map<String, long[]> byIp = new HashMap<>();
    jdbc.query("""
        SELECT ip, count(*) AS total FROM public.audit_log
        WHERE outcome = 'DENIED' AND occurred_at >= ? AND ip IS NOT NULL
        GROUP BY ip ORDER BY total DESC LIMIT 50
        """, rs -> {
      byIp.computeIfAbsent(rs.getString("ip"), k -> new long[5])[0] = rs.getLong("total");
    }, from);
    jdbc.query("""
        SELECT ip, kind, count(*) + sum(suppressed) AS total FROM gateway_event
        WHERE occurred_at >= ? GROUP BY ip, kind ORDER BY total DESC LIMIT 200
        """, rs -> {
      int slot = switch (rs.getString("kind")) {
        case "RATE_LIMITED" -> 1;
        case "AUTH_REJECTED" -> 2;
        case "NOT_FOUND" -> 3;
        default -> -1;
      };
      if (slot > 0) {
        byIp.computeIfAbsent(rs.getString("ip"), k -> new long[5])[slot] = rs.getLong("total");
      }
    }, from);
    authEvents.failedPerIp(from.toInstant(), 50).ifPresent(counts -> counts.forEach(
        (ip, total) -> byIp.computeIfAbsent(ip, k -> new long[5])[4] = total));
    List<Suspect> addresses = byIp.entrySet().stream()
        .map(e -> new Suspect(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2],
            e.getValue()[3], e.getValue()[4]))
        .sorted(Comparator.comparingLong(MonitorQueryService::score).reversed()
            .thenComparing(Suspect::ip))
        .limit(SUSPECT_LIMIT).toList();
    List<ErrorPath> paths = jdbc.query("""
        SELECT path, count(*) + sum(suppressed) AS total FROM gateway_event
        WHERE kind IN ('SERVER_ERROR', 'UPSTREAM_DOWN') AND occurred_at >= ? AND path IS NOT NULL
        GROUP BY path ORDER BY total DESC, path LIMIT ?
        """, (rs, i) -> new ErrorPath(rs.getString("path"), rs.getLong("total")), from,
        PATH_LIMIT);
    return new Suspects(window.code(), addresses, paths);
  }

  public Health health() {
    List<ServiceState> services = jdbc.query("""
        SELECT DISTINCT ON (service) service, state, detail, occurred_at
        FROM service_health_event ORDER BY service, occurred_at DESC, id DESC
        """, (rs, i) -> new ServiceState(rs.getString("service"), rs.getString("state"),
        rs.getString("detail"), rs.getTimestamp("occurred_at").toInstant()));
    List<StateChange> changes = jdbc.query(
        """
            SELECT service, state, detail, occurred_at,
              lead(occurred_at) OVER (PARTITION BY service ORDER BY occurred_at, id) AS next_at
            FROM service_health_event WHERE occurred_at >= ? ORDER BY occurred_at DESC, id DESC LIMIT 300
            """,
        (rs, i) -> {
          Timestamp next = rs.getTimestamp("next_at");
          Instant at = rs.getTimestamp("occurred_at").toInstant();
          Long downSeconds = DOWN.equals(rs.getString("state")) && next != null
              ? next.toInstant().getEpochSecond() - at.getEpochSecond()
              : null;
          return new StateChange(rs.getString("service"), rs.getString("state"),
              rs.getString("detail"), at, downSeconds);
        }, Timestamp.from(Window.LAST_WEEK.start()));
    return new Health(
        services.stream().sorted(Comparator.comparing(ServiceState::service)).toList(), changes);
  }

  public List<AdminAction> adminActions(int limit) {
    return jdbc.query("""
        SELECT occurred_at, action, outcome, actor_name, actor_roles, target_type, target_id, ip,
          path, request_id
        FROM public.audit_log WHERE outcome <> 'DENIED' AND action <> 'SERVER_ERROR'
        ORDER BY id DESC LIMIT ?
        """, (rs, i) -> new AdminAction(rs.getTimestamp("occurred_at").toInstant(),
        rs.getString("action"), rs.getString("outcome"), rs.getString("actor_name"),
        rs.getString("actor_roles"), target(rs.getString("target_type"), rs.getString("target_id")),
        rs.getString("ip"), rs.getString("path"), rs.getString("request_id")),
        Math.min(Math.max(limit, 1), 100));
  }

  // The newest records of the gateway, the backend and Keycloak. A request id and a client address
  // narrow them down (both together keep what matches both); neither gives the latest of all.
  public List<TraceEntry> trace(String requestId, String ip) {
    String request = blankToNull(requestId);
    String address = blankToNull(ip);
    if (request != null && !REQUEST_ID.matcher(request).matches()) {
      throw new IllegalArgumentException("requestId is malformed");
    }
    if (address != null && !ADDRESS.matcher(address).matches()) {
      throw new IllegalArgumentException("ip is malformed");
    }
    StringBuilder where = new StringBuilder(" WHERE true");
    List<Object> args = new ArrayList<>();
    if (request != null) {
      where.append(" AND request_id = ?");
      args.add(request);
    }
    if (address != null) {
      where.append(" AND ip = ?");
      args.add(address);
    }
    List<Object> all = new ArrayList<>(args);
    all.addAll(args);
    all.add(TRACE_LIMIT);
    List<TraceEntry> entries = new ArrayList<>(jdbc.query("""
        SELECT occurred_at, 'GATEWAY' AS source, kind AS what, status::text AS result, ip,
          method, path, request_id, host AS detail
        FROM gateway_event%1$s
        UNION ALL
        SELECT occurred_at, 'BACKEND', action, outcome, ip, http_method, path, request_id,
          actor_name
        FROM public.audit_log%1$s
        ORDER BY occurred_at DESC LIMIT ?
        """.formatted(where),
        (rs, i) -> new TraceEntry(rs.getTimestamp("occurred_at").toInstant(),
            rs.getString("source"), rs.getString("what"), rs.getString("result"),
            rs.getString("ip"), rs.getString("method"), rs.getString("path"),
            rs.getString("request_id"), rs.getString("detail")),
        all.toArray()));
    if (request == null) {
      entries.addAll(authEvents.recent(address, TRACE_LIMIT));
    }
    return entries.stream().sorted(Comparator.comparing(TraceEntry::at).reversed())
        .limit(TRACE_LIMIT).toList();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private long count(String sql, Object... args) {
    Long total = jdbc.queryForObject(sql, Long.class, args);
    return total == null ? 0 : total;
  }

  private long gatewayCount(String condition, Timestamp from) {
    return count("SELECT count(*) + coalesce(sum(suppressed), 0) FROM gateway_event WHERE "
        + condition + " AND occurred_at >= ?", from);
  }

  private static long score(Suspect suspect) {
    return suspect.denied() + suspect.rateLimited() + suspect.authRejected() + suspect.notFound()
        + suspect.failedLogins();
  }

  private static String target(String type, String id) {
    if (type == null) {
      return id;
    }
    return id == null ? type : type + " " + id;
  }

  // The events of one column while it is being filled. Unknown paths are left out of the times
  // because the screen does not chart them.
  private static final class Counts {

    private long denied;
    private long rateLimited;
    private long authRejected;
    private long notFound;
    private long serverErrors;
    private long slow;
    private Instant first;
    private Instant last;

    private void add(String kind, long total, Instant firstAt,
        Instant lastAt) {switch(kind){case"RATE_LIMITED"->rateLimited+=total;case"AUTH_REJECTED"->authRejected+=total;case"NOT_FOUND"->notFound+=total;case"SLOW"->slow+=total;default->serverErrors+=total;}if(!"NOT_FOUND".equals(kind)){seen(firstAt,lastAt);}}

    private void seen(Instant firstAt, Instant lastAt) {
      first = first == null || firstAt.isBefore(first) ? firstAt : first;
      last = last == null || lastAt.isAfter(last) ? lastAt : last;
    }
  }
}
