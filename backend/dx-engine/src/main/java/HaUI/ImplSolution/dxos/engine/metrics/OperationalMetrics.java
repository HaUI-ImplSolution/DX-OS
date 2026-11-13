// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.metrics;

import HaUI.ImplSolution.dxos.engine.apply.Sql;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.MultiGauge.Row;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// The operational numbers a monitoring tool scrapes: records by state and overdue records of each
// entity with a process, the queue of outgoing events and the applies of models. They are counted
// from the database every refresh period, so a scrape reads a stored value and never queries the
// data. Labels are only the model, the entity, the state and the result of an apply: names the
// model files declare, never a record, a user or free text, so the series count follows the
// models and not the data.
@Component
public class OperationalMetrics {

  private static final Logger log = LoggerFactory.getLogger(OperationalMetrics.class);

  // Series a model file can add are bounded by its declaration; this stops a pathological set.
  static final int MAX_RECORD_SERIES = 5000;

  private final JdbcTemplate jdbc;
  private final ModelRegistry registry;
  private final MultiGauge records;
  private final MultiGauge overdue;
  private volatile Snapshot snapshot = new Snapshot(0, 0, 0, 0, 0, 0);

  public OperationalMetrics(JdbcTemplate jdbc, ModelRegistry registry, MeterRegistry meters) {
    this.jdbc = jdbc;
    this.registry = registry;
    this.records =
        MultiGauge.builder("dxos.records")
            .description("Records of an entity with a process, by state")
            .register(meters);
    this.overdue =
        MultiGauge.builder("dxos.records.overdue")
            .description("Records whose handler ran out of time")
            .register(meters);
    Gauge.builder("dxos.models", this, m -> m.snapshot.models())
        .description("Models the system runs")
        .register(meters);
    Gauge.builder("dxos.outbox.pending", this, m -> m.snapshot.pending())
        .description("Outgoing events not yet delivered")
        .register(meters);
    Gauge.builder("dxos.outbox.retrying", this, m -> m.snapshot.retrying())
        .description("Outgoing events not delivered that failed at least once")
        .register(meters);
    Gauge.builder("dxos.outbox.oldest.age", this, m -> m.snapshot.oldestAgeSeconds())
        .description("Age of the oldest outgoing event not yet delivered")
        .baseUnit("seconds")
        .register(meters);
    for (String result : List.of("done", "failed")) {
      FunctionCounter.builder(
              "dxos.model.applies",
              this,
              m -> "done".equals(result) ? m.snapshot.appliesDone() : m.snapshot.appliesFailed())
          .description("Applies of a model file, by result")
          .tag("result", result)
          .register(meters);
    }
  }

  @PostConstruct
  @Scheduled(fixedDelayString = "${metrics.refresh-ms:30000}")
  public void refresh() {
    try {
      Map<String, Object> outbox =
          jdbc.queryForMap(
              "SELECT count(*) AS pending, count(*) FILTER (WHERE attempts > 0) AS retrying, "
                  + "coalesce(extract(epoch FROM now() - min(created_at)), 0) AS oldest "
                  + "FROM engine.outbox WHERE delivered_at IS NULL");
      Map<String, Object> applies =
          jdbc.queryForMap(
              "SELECT count(*) FILTER (WHERE status = 'done') AS done, "
                  + "count(*) FILTER (WHERE status = 'failed') AS failed FROM engine.apply_run");
      List<Row<?>> byState = new ArrayList<>();
      List<Row<?>> late = new ArrayList<>();
      Model[] models = registry.all().toArray(Model[]::new);
      for (Model model : models) {
        for (Entity entity : model.entities().values()) {
          if (entity.process() != null && byState.size() < MAX_RECORD_SERIES) {
            count(model, entity, byState, late);
          }
        }
      }
      records.register(byState, true);
      overdue.register(late, true);
      snapshot =
          new Snapshot(
              models.length,
              number(outbox.get("pending")),
              number(outbox.get("retrying")),
              number(outbox.get("oldest")),
              number(applies.get("done")),
              number(applies.get("failed")));
    } catch (RuntimeException e) {
      log.warn("The operational numbers were not refreshed: {}", e.getMessage());
    }
  }

  // A state with no record counts 0, so its series stays and a graph shows it empty.
  private void count(Model model, Entity entity, List<Row<?>> byState, List<Row<?>> late) {
    Map<String, Long> counts = new LinkedHashMap<>();
    entity.process().definition().states().forEach(state -> counts.put(state.name(), 0L));
    try {
      String table = Sql.table(model, entity);
      jdbc.query(
          "SELECT status, count(*) AS count FROM " + table + " GROUP BY status",
          rs -> {
            counts.put(rs.getString("status"), rs.getLong("count"));
          });
      Long overdueCount =
          jdbc.queryForObject(
              "SELECT count(*) FROM "
                  + table
                  + " WHERE handle_by IS NOT NULL AND handle_by < now()",
              Long.class);
      Tags entityTags = Tags.of("model", model.name(), "entity", entity.name());
      counts.forEach(
          (state, count) ->
              byState.add(Row.of(entityTags.and("status", state), count.doubleValue())));
      late.add(Row.of(entityTags, overdueCount == null ? 0 : overdueCount.doubleValue()));
    } catch (RuntimeException e) {
      // The table can be missing while a model is being applied; the next refresh counts it.
      log.debug(
          "Records of {}.{} were not counted: {}", model.name(), entity.name(), e.getMessage());
    }
  }

  private static double number(Object value) {
    return value instanceof Number n ? n.doubleValue() : 0;
  }

  private record Snapshot(
      double models,
      double pending,
      double retrying,
      double oldestAgeSeconds,
      double appliesDone,
      double appliesFailed) {}
}
