// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import HaUI.ImplSolution.dxos.engine.apply.Sql;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Writes one "overdue" line to the history of each record whose handler ran out of time. A record
// that waits for a handler again gets a new deadline and can be reported again.
@Component
public class OverdueSweep {

  private final JdbcTemplate jdbc;
  private final ModelRegistry registry;
  private final JobLock lock;

  public OverdueSweep(JdbcTemplate jdbc, ModelRegistry registry, JobLock lock) {
    this.jdbc = jdbc;
    this.registry = registry;
    this.lock = lock;
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
    jdbc.update("INSERT INTO engine.record_event (model, entity, record_id, action, actor, "
        + "actor_name, from_status, to_status) SELECT ?, ?, t.id, 'overdue', 'system', 'System', "
        + "t.status, t.status FROM " + Sql.table(model, entity) + " t "
        + "WHERE t.handle_by IS NOT NULL AND t.handle_by < now() AND NOT EXISTS (SELECT 1 FROM "
        + "engine.record_event e WHERE e.model = ? AND e.entity = ? AND e.record_id = t.id "
        + "AND e.action = 'overdue' AND e.at >= t.handle_by)", model.name(), entity.name(),
        model.name(), entity.name());
  }
}
