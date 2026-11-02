// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.engine.apply.Sql;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// The only writer of the status column. A process calls it from its own steps, inside the
// transaction of the step.
@Component("recordStatus")
public class RecordStatus {

  private final JdbcTemplate jdbc;
  private final ModelRegistry registry;

  public RecordStatus(JdbcTemplate jdbc, ModelRegistry registry) {
    this.jdbc = jdbc;
    this.registry = registry;
  }

  public void set(DelegateExecution execution, String status) {
    Model model = registry.require((String) execution.getVariable("model"));
    Entity entity = model.entity((String) execution.getVariable("entity")).orElseThrow();
    long id = ((Number) execution.getVariable("recordId")).longValue();
    jdbc.update(
        "UPDATE " + Sql.table(model, entity)
            + " SET status = ?, updated_at = now(), version = version + 1 WHERE id = ?",
        status, id);
  }
}
