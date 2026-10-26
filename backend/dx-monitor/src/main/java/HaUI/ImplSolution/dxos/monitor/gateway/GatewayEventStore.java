// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.gateway;

import HaUI.ImplSolution.dxos.monitor.config.MonitorProperties;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Keeps the gateway events, at most a set number of rows per client address each minute.
@Repository
public class GatewayEventStore {

  private final JdbcTemplate jdbc;
  private final IpMinuteLimiter limiter;
  private final int maxPerIpMinute;

  public GatewayEventStore(JdbcTemplate jdbc, IpMinuteLimiter limiter,
      MonitorProperties properties) {
    this.jdbc = jdbc;
    this.limiter = limiter;
    this.maxPerIpMinute = properties.gateway().maxPerIpMinute();
  }

  // Keeps the events within the share of each address and returns how many were kept. An event
  // over the share is counted in the suppressed column of the latest kept event of the same
  // address and kind, so the figures stay complete while the table stays small.
  public int saveAll(List<GatewayEvent> events) {
    long now = System.currentTimeMillis();
    int kept = 0;
    Map<String, Overflow> overflow = new LinkedHashMap<>();
    for (GatewayEvent event : events) {
      if (limiter.admit(event.ip(), maxPerIpMinute, now)) {
        insert(event);
        kept++;
      } else {
        overflow.computeIfAbsent(event.ip() + "|" + event.kind().name(),
            key -> new Overflow(event)).count++;
      }
    }
    for (Overflow over : overflow.values()) {
      int updated = jdbc.update("""
          UPDATE gateway_event SET suppressed = suppressed + ?
          WHERE id = (SELECT max(id) FROM gateway_event WHERE ip = ? AND kind = ?)
          """, over.count, over.first.ip(), over.first.kind().name());
      if (updated == 0) {
        insert(over.first.withSuppressed(over.count - 1));
        kept++;
      }
    }
    return kept;
  }

  private void insert(GatewayEvent event) {
    jdbc.update("""
        INSERT INTO gateway_event (occurred_at, kind, ip, host, method, path, status,
          request_time_ms, upstream, request_id, cf_ray, user_agent, suppressed)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, Timestamp.from(event.occurredAt()), event.kind().name(), event.ip(), event.host(),
        event.method(), event.path(), event.status(), event.requestTimeMs(), event.upstream(),
        event.requestId(), event.cfRay(), event.userAgent(), event.suppressed());
  }

  private static final class Overflow {

    private final GatewayEvent first;
    private int count;

    private Overflow(GatewayEvent first) {
      this.first = first;
    }
  }

  public int deleteOlderThan(Duration age) {
    return jdbc.update("DELETE FROM gateway_event WHERE occurred_at < ?",
        Timestamp.from(Instant.now().minus(age)));
  }
}
