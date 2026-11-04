// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Puts each line of a record's history in the outbox, in the transaction that wrote the line, so
// the webhook gets it once the change has committed and never when it rolls back. Nothing is
// written while no webhook address is set. The message holds ids, statuses and the time, never the
// content of the record or the note of the line.
@Component
public class RecordEventPublisher {

  private final Outbox outbox;
  private final boolean enabled;

  public RecordEventPublisher(Outbox outbox, @Value("${engine.webhook.url:}") String url) {
    this.outbox = outbox;
    this.enabled = WebhookHandler.usable(url);
  }

  public void publish(long eventId, String model, String entity, long recordId, String action,
      String fromStatus, String toStatus, String actor, String actorName, Instant at) {
    if (!enabled) {
      return;
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", eventId);
    payload.put("model", model);
    payload.put("entity", entity);
    payload.put("recordId", recordId);
    payload.put("action", action);
    payload.put("fromStatus", fromStatus);
    payload.put("toStatus", toStatus);
    payload.put("actor", actor);
    payload.put("actorName", actorName);
    payload.put("at", at.toString());
    outbox.publish(WebhookHandler.TOPIC, "record_event:" + eventId, model, entity, recordId,
        payload);
  }
}
