// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import HaUI.ImplSolution.dxos.engine.apply.Sql;
import HaUI.ImplSolution.dxos.engine.data.HistoryChain;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Writes one "overdue" line to the history of each record whose handler ran out of time, and one
// "due_soon" line when the time left falls under the warning period. A deadline that is not longer
// than the warning period gets no "due_soon" line. A record that waits for a handler again gets a
// new deadline and can be reported again. Each line is linked into the history chain and goes to
// the outbox in the transaction that writes it. A record whose deadline has passed and that leaves
// it before the sweep ran gets the line from reportBeforeLeaving, in the transaction of the change.
@Component
public class OverdueSweep {

  private final JdbcTemplate jdbc;
  private final ModelRegistry registry;
  private final JobLock lock;
  private final RecordEventPublisher publisher;
  private final TransactionTemplate transaction;
  private final HistoryChain chain;
  private final Duration dueSoonBefore;

  public OverdueSweep(
      JdbcTemplate jdbc,
      ModelRegistry registry,
      JobLock lock,
      RecordEventPublisher publisher,
      PlatformTransactionManager transactions,
      HistoryChain chain,
      @Value("${engine.due-soon.before:24h}") Duration dueSoonBefore) {
    this.jdbc = jdbc;
    this.dueSoonBefore = dueSoonBefore;
    this.chain = chain;
    this.registry = registry;
    this.lock = lock;
    this.publisher = publisher;
    this.transaction = new TransactionTemplate(transactions);
  }

  @Scheduled(fixedDelayString = "${engine.overdue.sweep-ms:60000}")
  public void run() {
    lock.runIfFree("engine-overdue", this::sweep);
  }

  void sweep() {
    for (Model model : registry.all()) {
      for (Entity entity : model.entities().values()) {
        if (entity.process() != null && entity.process().deadline() != null) {
          report(model, entity);
        }
      }
    }
  }

  private void report(Model model, Entity entity) {
    transaction.executeWithoutResult(
        status -> {
          chain.lock();
          List<Map<String, Object>> rows =
              new ArrayList<>(
                  insert(
                      model,
                      entity,
                      "overdue",
                      "t.handle_by < now()",
                      "e.at >= t.handle_by",
                      List.of(),
                      null));
          if (!dueSoonBefore.isZero() && entity.process().deadline().compareTo(dueSoonBefore) > 0) {
            double seconds = dueSoonBefore.toSeconds();
            rows.addAll(
                insert(
                    model,
                    entity,
                    "due_soon",
                    "t.handle_by >= now() AND t.handle_by <= now() + make_interval(secs => ?)",
                    "e.at >= t.handle_by - make_interval(secs => ?)",
                    List.of(seconds, seconds),
                    null));
          }
          publish(model, entity, rows);
        });
  }

  // Writes the "overdue" line of a record whose deadline has passed and that has none for this
  // deadline yet, in the transaction of the caller. A change of the record calls it before it
  // replaces or clears the deadline, so the line comes before the line of the change.
  public void reportBeforeLeaving(Model model, Entity entity, long recordId) {
    if (entity.process().deadline() == null) {
      return;
    }
    chain.lock();
    publish(
        model,
        entity,
        insert(
            model,
            entity,
            "overdue",
            "t.handle_by < now()",
            "e.at >= t.handle_by",
            List.of(),
            recordId));
  }

  private void publish(Model model, Entity entity, List<Map<String, Object>> rows) {
    List<Map<String, Object>> ordered = new ArrayList<>(rows);
    ordered.sort(Comparator.comparingLong(row -> ((Number) row.get("id")).longValue()));
    for (Map<String, Object> row : ordered) {
      long id = ((Number) row.get("id")).longValue();
      String hash = chain.link(id);
      publisher.publish(
          id,
          model.name(),
          entity.name(),
          ((Number) row.get("record_id")).longValue(),
          (String) row.get("action"),
          (String) row.get("from_status"),
          (String) row.get("to_status"),
          "system",
          "System",
          instant(row.get("at")),
          hash);
    }
  }

  // Writes the line of the records that meet the time condition and have no such line since the
  // given moment, of one record when recordId is given. The seconds are the parameters of the two
  // conditions, in the order they appear.
  private List<Map<String, Object>> insert(
      Model model,
      Entity entity,
      String action,
      String due,
      String reported,
      List<Double> seconds,
      Long recordId) {
    List<Object> args = new ArrayList<>(List.of(model.name(), entity.name()));
    if (seconds.size() == 2) {
      args.add(seconds.get(0));
    }
    args.add(model.name());
    args.add(entity.name());
    if (seconds.size() == 2) {
      args.add(seconds.get(1));
    }
    if (recordId != null) {
      args.add(recordId);
    }
    return jdbc.queryForList(
        "INSERT INTO engine.record_event (model, entity, record_id, action, actor, actor_name, "
            + "from_status, to_status) SELECT ?, ?, t.id, '"
            + action
            + "', 'system', 'System', "
            + "t.status, t.status FROM "
            + Sql.table(model, entity)
            + " t "
            + "WHERE t.handle_by IS NOT NULL AND "
            + due
            + " AND NOT EXISTS (SELECT 1 "
            + "FROM engine.record_event e WHERE e.model = ? AND e.entity = ? "
            + "AND e.record_id = t.id AND e.action = '"
            + action
            + "' AND "
            + reported
            + ")"
            + (recordId == null ? " " : " AND t.id = ? ")
            + "RETURNING id, record_id, action, from_status, to_status, at",
        args.toArray());
  }

  private static Instant instant(Object at) {
    return at instanceof OffsetDateTime time
        ? time.toInstant()
        : ((java.sql.Timestamp) at).toInstant();
  }
}
