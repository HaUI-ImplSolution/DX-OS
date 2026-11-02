// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// The history of a record. An event is written in the transaction of the change it describes, so
// the two commit or roll back together.
@Component
public class EventLog {

  private final JdbcTemplate jdbc;
  private final UserContext user;

  public EventLog(JdbcTemplate jdbc, UserContext user) {
    this.jdbc = jdbc;
    this.user = user;
  }

  public void record(Model model, Entity entity, long id, String action, String fromStatus,
      String toStatus, String note, String detail) {
    jdbc.update(
        "INSERT INTO engine.record_event (model, entity, record_id, action, actor, actor_name, "
            + "from_status, to_status, note, detail) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        model.name(), entity.name(), id, action, user.currentUserId(), user.currentFullName(),
        fromStatus, toStatus, note, detail);
  }

  public List<Map<String, Object>> of(Model model, Entity entity, long id) {
    return jdbc.queryForList(
        "SELECT id, action, actor, actor_name, at, from_status, to_status, note, detail "
            + "FROM engine.record_event WHERE model = ? AND entity = ? AND record_id = ? "
            + "ORDER BY id",
        model.name(), entity.name(), id);
  }
}
