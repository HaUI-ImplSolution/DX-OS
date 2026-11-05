// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

// Puts a change made by an administrator in the outbox: the apply of a model or a change to an
// account. A message that belongs to a model goes to the webhook target of that model and one that
// belongs to none goes to the default target. Nothing is written while there is no target to send
// to. These messages are not lines of a record history, so the feed pulled by eventId does not hold
// them.
@Component
public class AdminEventPublisher {

  static final String TOPIC = "admin.event";

  private final Outbox outbox;
  private final ModelRegistry registry;
  private final WebhookTargets targets;

  public AdminEventPublisher(Outbox outbox, ModelRegistry registry, WebhookTargets targets) {
    this.outbox = outbox;
    this.registry = registry;
    this.targets = targets;
  }

  // kind names the change, for example MODEL_APPLIED or USER_ROLE_GRANTED; model is null for a
  // change that belongs to no model; facts holds ids and names, never a secret.
  public void publish(String kind, String model, String actor, String actorName,
      Map<String, Object> facts) {
    if (!targets.sendsFor(model == null ? Optional.empty() : registry.find(model))) {
      return;
    }
    String id = UUID.randomUUID().toString();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("schemaVersion", RecordEventPublisher.SCHEMA_VERSION);
    payload.put("id", id);
    payload.put("kind", kind);
    payload.put("model", model);
    payload.put("actor", actor);
    payload.put("actorName", actorName);
    payload.put("at", Instant.now().toString());
    payload.put("facts", facts == null ? Map.of() : facts);
    outbox.publish(TOPIC, "admin_event:" + id, model == null ? "" : model, "", 0, payload);
  }
}
