// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.monitor.config.MonitorProperties;
import HaUI.ImplSolution.dxos.monitor.gateway.GatewayEventFilter;
import HaUI.ImplSolution.dxos.monitor.gateway.GatewayEventStore;
import HaUI.ImplSolution.dxos.monitor.gateway.GatewayIngestController;
import HaUI.ImplSolution.dxos.monitor.gateway.IpMinuteLimiter;
import HaUI.ImplSolution.dxos.monitor.health.ServiceHealthStore;
import HaUI.ImplSolution.dxos.monitor.maintenance.RetentionJob;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// From a line of the gateway log to the figures of the monitor screen, on PostgreSQL.
@Testcontainers
class MonitorIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static JdbcTemplate jdbc;
  private static MonitorProperties properties;

  private GatewayEventStore events;
  private GatewayIngestController ingest;

  @BeforeAll
  static void migrate() {
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    flyway("filesystem:../dx-core/src/main/resources/db/migration/core", "history_core");
    flyway("classpath:db/migration/monitor", "history_monitor");
    jdbc.execute("CREATE TABLE vw_auth_events (type text, ip text, occurred_at timestamptz)");
    properties =
        new MonitorProperties(
            "http://localhost",
            new MonitorProperties.Gateway("key", 3, List.of("/files/"), 3),
            null,
            null,
            30,
            new MonitorProperties.KeycloakDb(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()),
            null);
  }

  private static void flyway(String location, String table) {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations(location)
        .table(table)
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .load()
        .migrate();
  }

  @BeforeEach
  void empty() {
    jdbc.execute("TRUNCATE gateway_event, service_health_event, vw_auth_events");
    events = new GatewayEventStore(jdbc, new IpMinuteLimiter(), properties);
    ingest = new GatewayIngestController(properties, new GatewayEventFilter(properties), events);
  }

  private static Map<String, Object> line(String ip, int status, String uri, double seconds) {
    return Map.of(
        "remote_addr",
        ip,
        "status",
        status,
        "uri",
        uri,
        "request_time",
        seconds,
        "upstream_addr",
        "10.0.0.2:8080, 10.0.0.3:8080",
        "request_id",
        "r-" + status,
        "user_agent",
        "-");
  }

  private static long rows(String where) {
    return jdbc.queryForObject("SELECT count(*) FROM gateway_event WHERE " + where, Long.class);
  }

  @Test
  void onlyLinesWorthLookingAtAreKept() {
    List<Map<String, Object>> batch =
        List.of(
            line("203.0.113.1", 200, "/api/v1/data", 0.1),
            line("203.0.113.1", 200, "/api/v1/data", 5),
            line("203.0.113.2", 200, "/files/big", 5),
            line("203.0.113.3", 503, "/api", 0.1),
            line("203.0.113.4", 500, "/api", 0.1),
            line("203.0.113.5", 401, "/monitor/x", 0.1),
            line("127.0.0.1", 500, "/api", 0.1),
            Map.of("status", "abc"));

    assertThat(ingest.receive("Monitor key", batch).getStatusCode().value()).isEqualTo(202);

    assertThat(jdbc.queryForList("SELECT kind FROM gateway_event ORDER BY kind", String.class))
        .containsExactly("SERVER_ERROR", "SLOW", "UPSTREAM_DOWN");
    assertThat(rows("upstream = '10.0.0.2:8080' AND user_agent IS NULL")).isEqualTo(3);
  }

  @Test
  void onlyTheGatewayMaySendLinesAndOnlySoMany() {
    assertThat(ingest.receive(null, List.of()).getStatusCode().value()).isEqualTo(401);
    assertThat(ingest.receive("Monitor other", List.of()).getStatusCode().value()).isEqualTo(401);
    List<Map<String, Object>> tooMany = new ArrayList<>();
    for (int i = 0; i < 1001; i++) {
      tooMany.add(Map.of());
    }
    assertThat(ingest.receive("Monitor key", tooMany).getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void aFloodFromOneAddressIsCountedInsteadOfStored() {
    List<Map<String, Object>> flood = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      flood.add(line("203.0.113.9", 429, "/api", 0.1));
    }

    ingest.receive("Monitor key", flood);

    assertThat(rows("true")).isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT sum(suppressed) FROM gateway_event", Long.class))
        .isEqualTo(7);
  }

  @Test
  void theScreenReadsWhatWasStored() {
    ingest.receive(
        "Monitor key",
        List.of(
            line("203.0.113.1", 429, "/api/a", 0.1),
            line("203.0.113.1", 404, "/api/b", 0.1),
            line("203.0.113.2", 502, "/api/c", 0.1)));
    jdbc.update(
        "INSERT INTO audit_log (occurred_at, action, outcome, ip, path, actor_name, request_id) "
            + "VALUES (now(), 'ACCESS_DENIED', 'DENIED', '203.0.113.1', '/api/a', 'an', 'r-429'), "
            + "(now(), 'USER_CREATED', 'SUCCESS', '203.0.113.8', '/api/u', 'root', 'r-1')");
    jdbc.update("INSERT INTO vw_auth_events VALUES ('LOGIN_ERROR', '203.0.113.1', now())");
    ServiceHealthStore health = new ServiceHealthStore(jdbc);
    health.record("backend", "DOWN", "timeout", Instant.now().minus(1, ChronoUnit.MINUTES));
    health.record("backend", "UP", null, Instant.now());
    AuthEventReader auth = new AuthEventReader(properties);
    MonitorQueryService query = new MonitorQueryService(jdbc, auth);

    for (Window window : Window.values()) {
      assertThat(query.summary(window)).isNotNull();
      assertThat(query.timeline(window)).isNotNull();
      assertThat(query.suspects(window)).isNotNull();
    }
    assertThat(query.health().toString()).contains("backend");
    assertThat(query.adminActions(10)).hasSize(1);
    assertThat(query.trace("r-429", null)).hasSize(2);
    assertThat(query.trace(null, "203.0.113.1")).isNotEmpty();
    assertThat(health.lastStates()).containsEntry("backend", "UP");
    assertThat(auth.countFailed(Instant.now().minus(1, ChronoUnit.HOURS))).contains(1L);
  }

  @Test
  void oldLinesAreRemovedAfterTheRetentionTime() {
    ServiceHealthStore health = new ServiceHealthStore(jdbc);
    health.record("backend", "UP", null, Instant.now().minus(40, ChronoUnit.DAYS));
    health.record("backend", "DOWN", null, Instant.now());
    jdbc.update(
        "INSERT INTO gateway_event (occurred_at, kind, ip, status, request_time_ms, suppressed) "
            + "VALUES (now() - interval '40 days', 'SLOW', 'x', 200, 1, 0)");

    new RetentionJob(events, health, properties).run();

    assertThat(rows("true")).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM service_health_event", Long.class))
        .isEqualTo(1);
  }

  @Test
  void aFailingCleanupDoesNotStopTheMonitor() {
    GatewayEventStore broken = mock(GatewayEventStore.class);
    when(broken.deleteOlderThan(any())).thenThrow(new IllegalStateException("down"));

    new RetentionJob(broken, new ServiceHealthStore(jdbc), properties).run();

    verify(broken).deleteOlderThan(any());
  }
}
