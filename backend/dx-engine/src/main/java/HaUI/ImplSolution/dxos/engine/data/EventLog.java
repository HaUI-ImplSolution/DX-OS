// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.engine.event.RecordEventPublisher;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

// The history of a record. An event is written in the transaction of the change it describes, so
// the two commit or roll back together; the same transaction puts it in the outbox for the webhook.
@Component
public class EventLog {

  private final JdbcTemplate jdbc;
  private final UserContext user;
  private final RecordEventPublisher publisher;

  public EventLog(JdbcTemplate jdbc, UserContext user, RecordEventPublisher publisher) {
    this.jdbc = jdbc;
    this.user = user;
    this.publisher = publisher;
  }

  public void record(Model model, Entity entity, long id, String action, String fromStatus,
      String toStatus, String note, String detail) {
    String actor = user.currentUserId();
    String actorName = user.currentFullName();
    jdbc.query(
        "INSERT INTO engine.record_event (model, entity, record_id, action, actor, actor_name, "
            + "from_status, to_status, note, detail) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "RETURNING id, at",
        (RowCallbackHandler) row -> publisher.publish(row.getLong("id"), model.name(),
            entity.name(), id, action, fromStatus, toStatus, actor, actorName,
            row.getObject("at", OffsetDateTime.class).toInstant()),
        model.name(), entity.name(), id, action, actor, actorName, fromStatus, toStatus, note,
        detail);
  }

  public List<Map<String, Object>> of(Model model, Entity entity, long id) {
    return jdbc.queryForList(
        "SELECT id, action, actor, actor_name, at, from_status, to_status, note, detail "
            + "FROM engine.record_event WHERE model = ? AND entity = ? AND record_id = ? "
            + "ORDER BY id",
        model.name(), entity.name(), id);
  }
}
