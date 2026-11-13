// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.engine.apply.DdlGenerator;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.ModelParser;
import io.micrometer.core.instrument.Meter;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// The numbers a scraper reads match the data, and the set of metric names and labels is fixed: a
// label that carries a record, a user or free text would grow the series with the data.
@Testcontainers
class OperationalMetricsTest {

  private static final String MODEL =
      """
      format: 0
      name: metricsdemo
      roles:
        - name: STAFF
      entities:
        ticket:
          fields:
            title: {type: text}
          access: {STAFF: all}
          process:
            family: work_item
            handler: STAFF
            deadline: 72h
      """;

  private static final Set<String> METRICS =
      Set.of(
          "dxos.records",
          "dxos.records.overdue",
          "dxos.models",
          "dxos.outbox.pending",
          "dxos.outbox.retrying",
          "dxos.outbox.oldest.age",
          "dxos.model.applies");
  private static final Set<String> LABELS = Set.of("model", "entity", "status", "result");

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @Test
  void numbersMatchTheDataAndOnlyDeclaredNamesAreLabels() {
    Model model = ModelParser.parse(MODEL);
    JdbcTemplate admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    admin.execute("CREATE ROLE " + DdlGenerator.REPORT_ROLE);
    admin.execute(DdlGenerator.createSchema(model));
    SingleConnectionDataSource session =
        new SingleConnectionDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), true);
    session.setSchema(model.schema());
    JdbcTemplate jdbc = new JdbcTemplate(session);
    jdbc.execute(DdlGenerator.desiredState(model, Map.of()));
    jdbc.execute(
        "INSERT INTO ticket (created_by, handle_by) VALUES ('a', now() - interval '2 hours'),"
            + " ('b', now() + interval '2 hours'), ('c', now() + interval '3 hours')");
    jdbc.execute(
        "CREATE SCHEMA engine; "
            + "CREATE TABLE engine.outbox (id BIGSERIAL, attempts INT NOT NULL DEFAULT 0,"
            + " created_at TIMESTAMPTZ NOT NULL DEFAULT now(), delivered_at TIMESTAMPTZ); "
            + "CREATE TABLE engine.apply_run (id BIGSERIAL, status TEXT NOT NULL)");
    jdbc.execute(
        "INSERT INTO engine.outbox (attempts, created_at, delivered_at) VALUES"
            + " (0, now() - interval '10 minutes', NULL), (3, now() - interval '1 minute', NULL),"
            + " (0, now(), now())");
    jdbc.execute(
        "INSERT INTO engine.apply_run (status) VALUES ('done'), ('done'), ('failed'), ('running')");
    ModelRegistry registry = mock(ModelRegistry.class);
    when(registry.all()).thenReturn(List.of(model));
    PrometheusMeterRegistry meters = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    new OperationalMetrics(jdbc, registry, meters).refresh();

    String text = meters.scrape();
    String initial = model.entity("ticket").orElseThrow().process().definition().initial();
    assertThat(text)
        .contains(
            "dxos_records{entity=\"ticket\",model=\"metricsdemo\",status=\"" + initial + "\"} 3.0")
        .contains("dxos_records_overdue{entity=\"ticket\",model=\"metricsdemo\"} 1.0")
        .contains("dxos_models 1.0")
        .contains("dxos_outbox_pending 2.0")
        .contains("dxos_outbox_retrying 1.0")
        .contains("dxos_model_applies_total{result=\"done\"} 2.0")
        .contains("dxos_model_applies_total{result=\"failed\"} 1.0");
    assertThat(
            model.entity("ticket").orElseThrow().process().definition().states().stream()
                .filter(state -> !state.name().equals(initial))
                .map(state -> "status=\"" + state.name() + "\"} 0.0"))
        .allSatisfy(zero -> assertThat(text).contains(zero));
    assertThat(
            meters.getMeters().stream()
                .map(meter -> meter.getId().getName())
                .filter(name -> name.startsWith("dxos."))
                .collect(Collectors.toSet()))
        .isEqualTo(METRICS);
    assertThat(
            meters.getMeters().stream()
                .map(Meter::getId)
                .filter(id -> id.getName().startsWith("dxos."))
                .flatMap(id -> id.getTags().stream())
                .map(tag -> tag.getKey())
                .collect(Collectors.toSet()))
        .isSubsetOf(LABELS);
  }
}
