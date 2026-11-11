// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

// Each line of a record's history tells the open pages that the inboxes changed and goes in the
// outbox, in the transaction that wrote the line, so the webhook gets it once the change has
// committed and never when it rolls back. Nothing is written to the outbox while the model has no
// webhook target. The message holds ids, statuses, the time and the hash of the line in the history
// chain, never the content of the record or the note of the line; a receiver that keeps the hashes
// holds a copy of the chain outside the database.
@Component
public class RecordEventPublisher {

  // Version of the message layout; it goes up when a field changes meaning or goes away.
  static final int SCHEMA_VERSION = 1;

  private final Outbox outbox;
  private final ModelRegistry registry;
  private final WebhookTargets targets;
  private final InboxSignal inbox;

  public RecordEventPublisher(
      Outbox outbox, ModelRegistry registry, WebhookTargets targets, InboxSignal inbox) {
    this.outbox = outbox;
    this.registry = registry;
    this.targets = targets;
    this.inbox = inbox;
  }

  public void publish(
      long eventId,
      String model,
      String entity,
      long recordId,
      String action,
      String fromStatus,
      String toStatus,
      String actor,
      String actorName,
      Instant at,
      String hash) {
    inbox.changed();
    if (registry.find(model).filter(targets::sends).isEmpty()) {
      return;
    }
    Map<String, Object> payload =
        message(
            eventId,
            model,
            entity,
            recordId,
            action,
            fromStatus,
            toStatus,
            actor,
            actorName,
            at,
            hash);
    outbox.publish(
        WebhookHandler.TOPIC, "record_event:" + eventId, model, entity, recordId, payload);
  }

  // The layout of one message, shared by the webhook and by the feed that is pulled by eventId.
  static Map<String, Object> message(
      long eventId,
      String model,
      String entity,
      long recordId,
      String action,
      String fromStatus,
      String toStatus,
      String actor,
      String actorName,
      Instant at,
      String hash) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("schemaVersion", SCHEMA_VERSION);
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
    payload.put("hash", hash);
    return payload;
  }
}
