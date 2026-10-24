// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.query;

import HaUI.ImplSolution.dxos.monitor.config.MonitorProperties;
import HaUI.ImplSolution.dxos.monitor.query.Views.TraceEntry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Reads the sign-in events of Keycloak through a read-only role that sees one view. When the
// Keycloak database cannot be read the result is empty, so the other figures still show.
@Component
public class AuthEventReader {

  private static final Logger log = LoggerFactory.getLogger(AuthEventReader.class);
  private static final String FAILED = "LOGIN_ERROR";

  private final HikariDataSource dataSource;
  private final JdbcTemplate jdbc;

  public AuthEventReader(MonitorProperties properties) {
    MonitorProperties.KeycloakDb db = properties.keycloakDb();
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(db.url());
    config.setUsername(db.username());
    config.setPassword(db.password());
    config.setMaximumPoolSize(2);
    config.setConnectionTimeout(3000);
    config.setReadOnly(true);
    // The pool starts without a connection, so a stopped Keycloak does not stop the monitor.
    config.setInitializationFailTimeout(-1);
    this.dataSource = new HikariDataSource(config);
    this.jdbc = new JdbcTemplate(dataSource);
  }

  @PreDestroy
  void close() {
    dataSource.close();
  }

  public Optional<Long> countFailed(Instant from) {
    return read(() -> jdbc.queryForObject(
        "SELECT count(*) FROM vw_auth_events WHERE type = ? AND occurred_at >= ?", Long.class,
        FAILED, Timestamp.from(from)));
  }

  // The failed sign-ins of one column and when the first and the last of them happened.
  public record BucketCount(long count, Instant first, Instant last) {}

  // Failed sign-ins per column of the given interval (a constant of Bucketing).
  public Optional<Map<Instant, BucketCount>> failedPerBucket(Instant from, Instant to,
      String interval) {
    return read(() -> {
      Map<Instant, BucketCount> counts = new LinkedHashMap<>();
      jdbc.query("SELECT date_bin(interval '" + interval + "', occurred_at, " + Bucketing.ORIGIN
          + ") AS bucket, count(*) AS total, min(occurred_at) AS first_at, "
          + "max(occurred_at) AS last_at FROM vw_auth_events "
          + "WHERE type = ? AND occurred_at >= ? AND occurred_at < ? GROUP BY bucket", rs -> {
            counts.put(rs.getTimestamp("bucket").toInstant(), new BucketCount(rs.getLong("total"),
                rs.getTimestamp("first_at").toInstant(), rs.getTimestamp("last_at").toInstant()));
          }, FAILED, Timestamp.from(from), Timestamp.from(to));
      return counts;
    });
  }

  public Optional<Map<String, Long>> failedPerIp(Instant from, int limit) {
    return read(() -> {
      Map<String, Long> counts = new LinkedHashMap<>();
      jdbc.query("""
          SELECT ip_address, count(*) AS total FROM vw_auth_events
          WHERE type = ? AND occurred_at >= ? AND ip_address IS NOT NULL
          GROUP BY ip_address ORDER BY total DESC LIMIT ?
          """, rs -> {
        counts.put(rs.getString("ip_address"), rs.getLong("total"));
      }, FAILED, Timestamp.from(from), limit);
      return counts;
    });
  }

  // The newest sign-in events, of one client address or (null) of all.
  public List<TraceEntry> recent(String ip, int limit) {
    return read(() -> {
      List<TraceEntry> entries = new ArrayList<>();
      jdbc.query("""
          SELECT occurred_at, type, error, ip_address, client_id, username
          FROM vw_auth_events WHERE (?::text IS NULL OR ip_address = ?)
          ORDER BY occurred_at DESC LIMIT ?
          """, rs -> {
        entries.add(new TraceEntry(rs.getTimestamp("occurred_at").toInstant(), "KEYCLOAK",
            rs.getString("type"), rs.getString("error"), rs.getString("ip_address"), null, null,
            null, join(rs.getString("client_id"), rs.getString("username"))));
      }, ip, ip, limit);
      return entries;
    }).orElse(List.of());
  }

  private static String join(String client, String username) {
    if (username == null) {
      return client;
    }
    return client == null ? username : client + " / " + username;
  }

  private <T> Optional<T> read(Supplier<T> query) {
    try {
      return Optional.ofNullable(query.get());
    } catch (RuntimeException e) {
      log.warn("Keycloak sign-in events are not readable: {}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }
}
