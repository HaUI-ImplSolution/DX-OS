// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.engine.apply.Sql;
import HaUI.ImplSolution.dxos.engine.event.CompletionHandler;
import HaUI.ImplSolution.dxos.engine.event.Outbox;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Completion;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.ProcessFamily;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// The only writer of the status column, and of who decided it and when. A process calls it from
// its own steps, inside the transaction of the step.
@Component("recordStatus")
public class RecordStatus {

  private final JdbcTemplate jdbc;
  private final ModelRegistry registry;
  private final Outbox outbox;

  public RecordStatus(JdbcTemplate jdbc, ModelRegistry registry, Outbox outbox) {
    this.jdbc = jdbc;
    this.registry = registry;
    this.outbox = outbox;
  }

  public void set(DelegateExecution execution, String status) {
    Model model = registry.require((String) execution.getVariable("model"));
    Entity entity = model.entity((String) execution.getVariable("entity")).orElseThrow();
    long id = ((Number) execution.getVariable("recordId")).longValue();
    write(model, entity, id, status, (String) execution.getVariable("actor"));
  }

  // Writes the status, who decided it and when once it is final, and the deadline of the handler
  // while the record waits for one.
  public void write(Model model, Entity entity, long id, String status, String actor) {
    ProcessFamily family = entity.process().definition();
    Duration deadline = entity.process().deadline();
    boolean terminal = family.isTerminal(status);
    List<Object> args = new ArrayList<>();
    args.add(status);
    StringBuilder sql = new StringBuilder("UPDATE " + Sql.table(model, entity) + " SET status = ?");
    if (terminal) {
      sql.append(", decided_by = ?, decided_at = now()");
      args.add(actor);
    }
    if (deadline != null && family.awaitsHandler(status)) {
      sql.append(", handle_by = now() + make_interval(secs => ?)");
      args.add(deadline.toSeconds());
    } else {
      sql.append(", handle_by = NULL");
    }
    sql.append(", updated_at = now(), version = version + 1 WHERE id = ?");
    args.add(id);
    jdbc.update(sql.toString(), args.toArray());
    if (terminal) {
      publishCompletions(model, entity, id, status);
    }
  }

  // The actions a model asks for in a final state go to the outbox in this transaction, so they
  // are sent once the decision has committed and never when it rolls back.
  private void publishCompletions(Model model, Entity entity, long id, String status) {
    List<Completion> completions = entity.process().after().getOrDefault(status, List.of());
    if (completions.isEmpty()) {
      return;
    }
    Map<String, Object> row =
        jdbc.queryForMap("SELECT * FROM " + Sql.table(model, entity) + " WHERE id = ?", id);
    for (int i = 0; i < completions.size(); i++) {
      Completion completion = completions.get(i);
      Object user = row.get(completion.user());
      Object group =
          completion.group() != null ? completion.group() : row.get(completion.groupField());
      if (user == null || group == null || group.toString().isBlank()) {
        continue;
      }
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("operation", completion.operation());
      payload.put("user", user.toString());
      payload.put("group", group.toString());
      outbox.publish(CompletionHandler.TOPIC,
          "completion:" + model.name() + "." + entity.name() + "." + id + ":" + status + ":" + i,
          model.name(), entity.name(), id, payload);
    }
  }
}
