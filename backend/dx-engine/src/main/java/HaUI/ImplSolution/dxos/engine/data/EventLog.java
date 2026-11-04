// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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

  public record Window(List<Map<String, Object>> items, long latestId, boolean more) {}

  public Window window(Model model, Entity entity, long id, Instant from, Instant to, int limit) {
    String where = " WHERE model = ? AND entity = ? AND record_id = ?";
    List<Object> args = new ArrayList<>(List.of(model.name(), entity.name(), id));
    Long latest = jdbc.queryForObject(
        "SELECT coalesce(max(id), 0) FROM engine.record_event" + where, Long.class, args.toArray());
    if (from != null) {
      where += " AND at >= ?";
      args.add(Timestamp.from(from));
    }
    if (to != null) {
      where += " AND at <= ?";
      args.add(Timestamp.from(to));
    }
    args.add(limit + 1);
    List<Map<String, Object>> items =
        jdbc.queryForList("SELECT id, action, actor_name, at, from_status, to_status, note, detail "
            + "FROM engine.record_event" + where + " ORDER BY id LIMIT ?", args.toArray());
    return new Window(items.stream().limit(limit).toList(), latest == null ? 0 : latest,
        items.size() > limit);
  }
}
