// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import java.time.Duration;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// Writes a message to the outbox in the transaction of the change it follows. Publishing a key
// that is already there does nothing, so a step that runs twice sends one message.
@Component
public class Outbox {

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public Outbox(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public void publish(
      String topic,
      String key,
      String model,
      String entity,
      long recordId,
      Map<String, Object> payload) {
    publishLater(topic, key, model, entity, recordId, payload, Duration.ZERO);
  }

  // Like publish, but the first try waits for the delay.
  public void publishLater(
      String topic,
      String key,
      String model,
      String entity,
      long recordId,
      Map<String, Object> payload,
      Duration delay) {
    jdbc.update(
        "INSERT INTO engine.outbox (topic, idempotency_key, model, entity, record_id, payload, "
            + "next_attempt_at) VALUES (?, ?, ?, ?, ?, ?::jsonb, now() + make_interval(secs => ?)) "
            + "ON CONFLICT (idempotency_key) DO NOTHING",
        topic,
        key,
        model,
        entity,
        recordId,
        json.writeValueAsString(payload),
        (double) delay.toSeconds());
  }
}
