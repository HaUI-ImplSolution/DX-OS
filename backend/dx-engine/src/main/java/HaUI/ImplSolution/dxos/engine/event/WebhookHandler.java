// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.engine.event.WebhookTargets.Target;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

// Posts the history lines of records to the webhook target of their model. A message may arrive
// twice, and a message that failed is sent again after the later ones of other records, so the
// receiver orders and drops repeats by eventId; the lines of one record are delivered in order.
@Component
public class WebhookHandler implements OutboxHandler {

  public static final String TOPIC = "record.event";

  private final ModelRegistry registry;
  private final WebhookTargets targets;
  private final WebhookSender sender;

  public WebhookHandler(ModelRegistry registry, WebhookTargets targets, WebhookSender sender) {
    this.registry = registry;
    this.targets = targets;
    this.sender = sender;
  }

  @Override
  public String topic() {
    return TOPIC;
  }

  @Override
  public void deliver(Map<String, Object> payload) {
    Optional<Model> model = registry.find(String.valueOf(payload.get("model")));
    if (model.isEmpty() || !targets.sends(model.get())) {
      return;
    }
    Optional<Target> target = targets.of(model.get());
    if (target.isEmpty()) {
      throw new IllegalStateException(
          "The webhook target " + model.get().webhook() + " is not configured");
    }
    boolean hasProcess =
        model
            .get()
            .entity(String.valueOf(payload.get("entity")))
            .map(entity -> entity.process() != null)
            .orElse(false);
    sender.send(
        target.get(),
        String.valueOf(payload.get("eventId")),
        payload,
        ChatText.record(payload, hasProcess) + escalatedTo(model.get(), payload));
  }

  // The roles an escalation notice is for, as a sentence tail; empty for any other message.
  private static String escalatedTo(Model model, Map<String, Object> payload) {
    if (!(payload.get("level") instanceof Number level)) {
      return "";
    }
    return model
        .entity(String.valueOf(payload.get("entity")))
        .filter(entity -> entity.process() != null)
        .map(entity -> entity.process().escalations())
        .filter(levels -> level.intValue() >= 1 && level.intValue() <= levels.size())
        .map(levels -> ", gửi tới vai " + String.join(", ", levels.get(level.intValue() - 1).to()))
        .orElse("");
  }
}
