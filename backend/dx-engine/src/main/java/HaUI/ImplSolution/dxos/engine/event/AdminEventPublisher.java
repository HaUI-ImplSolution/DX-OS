// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// Records a change made by an administrator, the apply of a model or a change to an account, and
// puts it in the outbox when there is a target to send it to. The line is written in every case, so
// the feed pulled by eventId holds it whether or not a webhook address is set. A message that
// belongs to a model goes to the webhook target of that model and one that belongs to none goes to
// the default target.
@Component
public class AdminEventPublisher {

  static final String TOPIC = "admin.event";

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final Outbox outbox;
  private final ModelRegistry registry;
  private final WebhookTargets targets;

  public AdminEventPublisher(
      JdbcTemplate jdbc,
      ObjectMapper json,
      Outbox outbox,
      ModelRegistry registry,
      WebhookTargets targets) {
    this.jdbc = jdbc;
    this.json = json;
    this.outbox = outbox;
    this.registry = registry;
    this.targets = targets;
  }

  // kind names the change, for example MODEL_APPLIED or USER_ROLE_GRANTED; model is null for a
  // change that belongs to no model; facts holds ids and names, never a secret.
  public void publish(
      String kind, String model, String actor, String actorName, Map<String, Object> facts) {
    Map<String, Object> shown = facts == null ? Map.of() : facts;
    Map<String, Object> row =
        jdbc.queryForMap(
            "INSERT INTO engine.admin_event (kind, model, actor, actor_name, facts) "
                + "VALUES (?, ?, ?, ?, ?::jsonb) RETURNING id, at",
            kind,
            model,
            actor,
            actorName,
            json.writeValueAsString(shown));
    if (!targets.sendsFor(model == null ? Optional.empty() : registry.find(model))) {
      return;
    }
    long id = ((Number) row.get("id")).longValue();
    outbox.publish(
        TOPIC,
        "admin_event:" + id,
        model == null ? "" : model,
        "",
        0,
        message(
            id, kind, model, actor, actorName, EventFeedController.instant(row.get("at")), shown));
  }

  // The layout of one message, shared by the webhook and by the feed that is pulled by eventId.
  static Map<String, Object> message(
      long eventId,
      String kind,
      String model,
      String actor,
      String actorName,
      Instant at,
      Map<String, Object> facts) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("schemaVersion", RecordEventPublisher.SCHEMA_VERSION);
    payload.put("eventId", eventId);
    payload.put("kind", kind);
    payload.put("model", model);
    payload.put("actor", actor);
    payload.put("actorName", actorName);
    payload.put("at", at.toString());
    payload.put("facts", facts);
    return payload;
  }
}
