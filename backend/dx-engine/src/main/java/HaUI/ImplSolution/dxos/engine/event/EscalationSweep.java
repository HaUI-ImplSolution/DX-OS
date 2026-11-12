// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import HaUI.ImplSolution.dxos.engine.apply.Sql;
import HaUI.ImplSolution.dxos.engine.data.HistoryChain;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Escalation;
import HaUI.ImplSolution.dxos.model.Escalation.Slot;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Writes one "escalated" line to the history of a record that stays unhandled after its deadline,
// each time a notice of the escalation levels of its process falls due. The line holds the level
// and the round as "level:round" in its detail, and goes to the outbox in the transaction that
// writes it. The line changes nothing about the record. A sweep that finds several notices due,
// as after the service was down, writes only the latest one, so the receivers get one message
// instead of a burst. A record that waits for a handler again gets a new deadline and starts over.
@Component
public class EscalationSweep {

  private final JdbcTemplate jdbc;
  private final ModelRegistry registry;
  private final JobLock lock;
  private final RecordEventPublisher publisher;
  private final TransactionTemplate transaction;
  private final HistoryChain chain;

  public EscalationSweep(
      JdbcTemplate jdbc,
      ModelRegistry registry,
      JobLock lock,
      RecordEventPublisher publisher,
      PlatformTransactionManager transactions,
      HistoryChain chain) {
    this.jdbc = jdbc;
    this.registry = registry;
    this.lock = lock;
    this.publisher = publisher;
    this.chain = chain;
    this.transaction = new TransactionTemplate(transactions);
  }

  @Scheduled(fixedDelayString = "${engine.overdue.sweep-ms:60000}")
  public void run() {
    lock.runIfFree("engine-escalation", this::sweep);
  }

  void sweep() {
    for (Model model : registry.all()) {
      for (Entity entity : model.entities().values()) {
        if (entity.process() != null && !entity.process().escalations().isEmpty()) {
          report(model, entity, Escalation.slots(entity.process().escalations()));
        }
      }
    }
  }

  // "level:round" for a line of an escalation, null for any other line.
  static int[] parse(String action, String detail) {
    if (!"escalated".equals(action) || detail == null) {
      return null;
    }
    String[] parts = detail.split(":");
    try {
      return parts.length == 2
          ? new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])}
          : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void report(Model model, Entity entity, List<Slot> slots) {
    transaction.executeWithoutResult(
        status -> {
          chain.lock();
          List<Map<String, Object>> written = new ArrayList<>();
          for (Map<String, Object> row : unhandled(model, entity, slots.get(0))) {
            Slot due = latestDue(slots, ((Number) row.get("late")).doubleValue());
            if (due != null && due.at().compareTo(sent(slots, (String) row.get("last"))) > 0) {
              written.addAll(write(model, entity, ((Number) row.get("id")).longValue(), due));
            }
          }
          for (Map<String, Object> row : written) {
            publish(model, entity, row);
          }
        });
  }

  // The records whose deadline has passed by at least the first notice, with the seconds they are
  // past it and the detail of the last notice sent for the current deadline.
  List<Map<String, Object>> unhandled(Model model, Entity entity, Slot first) {
    return jdbc.queryForList(
        "SELECT t.id, EXTRACT(EPOCH FROM (now() - t.handle_by)) AS late, "
            + "(SELECT e.detail FROM engine.record_event e WHERE e.model = ? AND e.entity = ? "
            + "AND e.record_id = t.id AND e.action = 'escalated' AND e.at >= t.handle_by "
            + "ORDER BY e.id DESC LIMIT 1) AS last FROM "
            + Sql.table(model, entity)
            + " t WHERE t.handle_by IS NOT NULL "
            + "AND t.handle_by < now() - make_interval(secs => ?)",
        model.name(),
        entity.name(),
        (double) first.at().toSeconds());
  }

  static Slot latestDue(List<Slot> slots, double lateSeconds) {
    Slot due = null;
    for (Slot slot : slots) {
      if (slot.at().toSeconds() <= lateSeconds) {
        due = slot;
      }
    }
    return due;
  }

  // The time past the deadline of the last notice sent, below zero when none was sent for this
  // deadline or the detail names a notice the levels no longer have.
  static Duration sent(List<Slot> slots, String detail) {
    int[] step = parse("escalated", detail);
    if (step == null) {
      return Duration.ofSeconds(-1);
    }
    return slots.stream()
        .filter(slot -> slot.level() == step[0] && slot.round() == step[1])
        .map(Slot::at)
        .findFirst()
        .orElse(Duration.ofSeconds(-1));
  }

  // Inserts the line unless the same notice was already written for the current deadline.
  private List<Map<String, Object>> write(Model model, Entity entity, long recordId, Slot slot) {
    String detail = slot.level() + ":" + slot.round();
    return jdbc.queryForList(
        "INSERT INTO engine.record_event (model, entity, record_id, action, actor, actor_name, "
            + "from_status, to_status, detail) SELECT ?, ?, t.id, 'escalated', 'system', 'System', "
            + "t.status, t.status, ? FROM "
            + Sql.table(model, entity)
            + " t WHERE t.id = ? AND t.handle_by IS NOT NULL "
            + "AND NOT EXISTS (SELECT 1 FROM engine.record_event e WHERE e.model = ? "
            + "AND e.entity = ? AND e.record_id = t.id AND e.action = 'escalated' "
            + "AND e.detail = ? AND e.at >= t.handle_by) "
            + "RETURNING id, record_id, action, from_status, to_status, at, detail",
        model.name(),
        entity.name(),
        detail,
        recordId,
        model.name(),
        entity.name(),
        detail);
  }

  private void publish(Model model, Entity entity, Map<String, Object> row) {
    long id = ((Number) row.get("id")).longValue();
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
        chain.link(id),
        (String) row.get("detail"));
  }

  private static Instant instant(Object at) {
    return at instanceof OffsetDateTime time
        ? time.toInstant()
        : ((java.sql.Timestamp) at).toInstant();
  }
}
