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
import org.springframework.stereotype.Component;

// The history of a record. An event is written in the transaction of the change it describes, so
// the two commit or roll back together; the same transaction links it into the history chain and
// puts it in the outbox for the webhook.
@Component
public class EventLog {

  private final JdbcTemplate jdbc;
  private final UserContext user;
  private final RecordEventPublisher publisher;
  private final HistoryChain chain;

  public EventLog(JdbcTemplate jdbc, UserContext user, RecordEventPublisher publisher,
      HistoryChain chain) {
    this.jdbc = jdbc;
    this.user = user;
    this.publisher = publisher;
    this.chain = chain;
  }

  public void record(Model model, Entity entity, long id, String action, String fromStatus,
      String toStatus, String note, String detail) {
    String actor = user.currentUserId();
    String actorName = user.currentFullName();
    chain.lock();
    Map<String, Object> row = jdbc.queryForMap(
        "INSERT INTO engine.record_event (model, entity, record_id, action, actor, actor_name, "
            + "from_status, to_status, note, detail) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "RETURNING id, at",
        model.name(), entity.name(), id, action, actor, actorName, fromStatus, toStatus, note,
        detail);
    long eventId = ((Number) row.get("id")).longValue();
    String hash = chain.link(eventId);
    publisher.publish(eventId, model.name(), entity.name(), id, action, fromStatus, toStatus, actor,
        actorName, instant(row.get("at")), hash);
  }

  private static java.time.Instant instant(Object at) {
    return at instanceof OffsetDateTime time ? time.toInstant()
        : ((java.sql.Timestamp) at).toInstant();
  }

  public List<Map<String, Object>> of(Model model, Entity entity, long id) {
    return jdbc.queryForList(
        "SELECT id, action, actor, actor_name, at, from_status, to_status, note, detail "
            + "FROM engine.record_event WHERE model = ? AND entity = ? AND record_id = ? "
            + "ORDER BY id",
        model.name(), entity.name(), id);
  }
}
