// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import HaUI.ImplSolution.dxos.engine.apply.Sql;
import HaUI.ImplSolution.dxos.engine.data.HistoryChain;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Writes one "overdue" line to the history of each record whose handler ran out of time. A record
// that waits for a handler again gets a new deadline and can be reported again. Each line is linked
// into the history chain and goes to the outbox in the transaction that writes it.
@Component
public class OverdueSweep {

  private final JdbcTemplate jdbc;
  private final ModelRegistry registry;
  private final JobLock lock;
  private final RecordEventPublisher publisher;
  private final TransactionTemplate transaction;
  private final HistoryChain chain;

  public OverdueSweep(JdbcTemplate jdbc, ModelRegistry registry, JobLock lock,
      RecordEventPublisher publisher, PlatformTransactionManager transactions, HistoryChain chain) {
    this.jdbc = jdbc;
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
    transaction.executeWithoutResult(status -> {
      chain.lock();
      List<Map<String, Object>> rows = new ArrayList<>(jdbc.queryForList(
          "INSERT INTO engine.record_event (model, entity, record_id, action, actor, actor_name, "
              + "from_status, to_status) SELECT ?, ?, t.id, 'overdue', 'system', 'System', "
              + "t.status, t.status FROM " + Sql.table(model, entity) + " t "
              + "WHERE t.handle_by IS NOT NULL AND t.handle_by < now() AND NOT EXISTS (SELECT 1 "
              + "FROM engine.record_event e WHERE e.model = ? AND e.entity = ? "
              + "AND e.record_id = t.id AND e.action = 'overdue' AND e.at >= t.handle_by) "
              + "RETURNING id, record_id, from_status, to_status, at",
          model.name(), entity.name(), model.name(), entity.name()));
      rows.sort(Comparator.comparingLong(row -> ((Number) row.get("id")).longValue()));
      for (Map<String, Object> row : rows) {
        long id = ((Number) row.get("id")).longValue();
        String hash = chain.link(id);
        publisher.publish(id, model.name(), entity.name(),
            ((Number) row.get("record_id")).longValue(), "overdue", (String) row.get("from_status"),
            (String) row.get("to_status"), "system", "System", instant(row.get("at")), hash);
      }
    });
  }

  private static Instant instant(Object at) {
    return at instanceof OffsetDateTime time ? time.toInstant()
        : ((java.sql.Timestamp) at).toInstant();
  }
}
