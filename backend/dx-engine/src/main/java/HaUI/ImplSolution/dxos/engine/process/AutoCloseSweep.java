// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import HaUI.ImplSolution.dxos.engine.apply.Sql;
import HaUI.ImplSolution.dxos.engine.data.HistoryChain;
import HaUI.ImplSolution.dxos.engine.event.RecordEventPublisher;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.ProcessFamily;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Closes a record that has waited for its owner for longer than the auto_close time of its entity
// without the owner acting on it. The record takes the auto_close action its owner would take, so
// the process, the final status and the completion actions run as for a
// confirmation, with "system" as the one who decided. An "auto_closed" line goes to the history
// chain and the outbox in the same transaction.
@Component
public class AutoCloseSweep {

  private static final Logger log = LoggerFactory.getLogger(AutoCloseSweep.class);
  private static final int BATCH = 100;
  private static final String SYSTEM = "system";

  private final JdbcTemplate jdbc;
  private final ModelRegistry registry;
  private final JobLock lock;
  private final ProcessService process;
  private final RecordEventPublisher publisher;
  private final HistoryChain chain;
  private final TransactionTemplate transaction;

  public AutoCloseSweep(JdbcTemplate jdbc, ModelRegistry registry, JobLock lock,
      ProcessService process, RecordEventPublisher publisher, HistoryChain chain,
      PlatformTransactionManager transactions) {
    this.jdbc = jdbc;
    this.registry = registry;
    this.lock = lock;
    this.process = process;
    this.publisher = publisher;
    this.chain = chain;
    this.transaction = new TransactionTemplate(transactions);
  }

  @Scheduled(fixedDelayString = "${engine.auto-close.sweep-ms:60000}")
  public void run() {
    lock.runIfFree("engine-auto-close", this::sweep);
  }

  void sweep() {
    for (Model model : registry.all()) {
      for (Entity entity : model.entities().values()) {
        if (entity.process() != null && entity.process().autoClose() != null) {
          close(model, entity);
        }
      }
    }
  }

  private void close(Model model, Entity entity) {
    ProcessFamily.Action action =
        entity.process().definition().action(entity.process().autoCloseAction()).orElseThrow();
    String table = Sql.table(model, entity);
    double seconds = entity.process().autoClose().toSeconds();
    List<Long> due =
        jdbc.queryForList(
            "SELECT t.id FROM " + table + " t WHERE " + dueCondition(action)
                + " ORDER BY t.id LIMIT " + BATCH,
            Long.class, params(model, entity, action, seconds));
    for (long id : due) {
      try {
        transaction.executeWithoutResult(status -> closeOne(model, entity, action, id, seconds));
      } catch (RuntimeException e) {
        log.warn("Auto close of {}.{} record {} failed: {}", model.name(), entity.name(), id,
            e.getMessage());
      }
    }
  }

  // The record waits in a state the action leaves, and it entered that state longer ago than the
  // auto_close time. Entering is the last history line that changed the status to the current one.
  private static String dueCondition(ProcessFamily.Action action) {
    String states = action.from().stream().map(state -> "?").collect(Collectors.joining(", "));
    return "t.status IN (" + states + ") AND (SELECT max(e.at) FROM engine.record_event e "
        + "WHERE e.model = ? AND e.entity = ? AND e.record_id = t.id AND e.to_status = t.status "
        + "AND e.from_status IS DISTINCT FROM e.to_status) < now() - make_interval(secs => ?)";
  }

  private static Object[] params(Model model, Entity entity, ProcessFamily.Action action,
      double seconds) {
    List<Object> params = new ArrayList<>(action.from());
    params.add(model.name());
    params.add(entity.name());
    params.add(seconds);
    return params.toArray();
  }

  // Locks the row and checks it again, so a record the owner has just taken the action on or sent
  // back is left alone.
  private void closeOne(Model model, Entity entity, ProcessFamily.Action action, long id,
      double seconds) {
    List<Object> args = new ArrayList<>();
    args.add(id);
    args.addAll(List.of(params(model, entity, action, seconds)));
    List<Long> locked =
        jdbc.queryForList("SELECT t.id FROM " + Sql.table(model, entity) + " t WHERE t.id = ? AND "
            + dueCondition(action) + " FOR UPDATE OF t", Long.class, args.toArray());
    if (locked.isEmpty()) {
      return;
    }
    String from = jdbc.queryForObject(
        "SELECT status FROM " + Sql.table(model, entity) + " WHERE id = ?", String.class, id);
    process.act(model, entity, id, action, SYSTEM);
    String to = jdbc.queryForObject(
        "SELECT status FROM " + Sql.table(model, entity) + " WHERE id = ?", String.class, id);
    chain.lock();
    Map<String, Object> row = jdbc.queryForMap(
        "INSERT INTO engine.record_event (model, entity, record_id, action, actor, actor_name, "
            + "from_status, to_status) VALUES (?, ?, ?, 'auto_closed', 'system', 'System', ?, ?) "
            + "RETURNING id, at",
        model.name(), entity.name(), id, from, to);
    long eventId = ((Number) row.get("id")).longValue();
    String hash = chain.link(eventId);
    publisher.publish(eventId, model.name(), entity.name(), id, "auto_closed", from, to, SYSTEM,
        "System", instant(row.get("at")), hash);
  }

  private static Instant instant(Object at) {
    return at instanceof OffsetDateTime time ? time.toInstant()
        : ((java.sql.Timestamp) at).toInstant();
  }
}
