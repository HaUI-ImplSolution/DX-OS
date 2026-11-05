// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.engine.event.WebhookTargets.Target;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

// Delivers the changes made by administrators to the webhook target of their model, or to the
// default target when they belong to no model.
@Component
public class AdminEventHandler implements OutboxHandler {

  private final ModelRegistry registry;
  private final WebhookTargets targets;
  private final WebhookSender sender;

  public AdminEventHandler(ModelRegistry registry, WebhookTargets targets, WebhookSender sender) {
    this.registry = registry;
    this.targets = targets;
    this.sender = sender;
  }

  @Override
  public String topic() {
    return AdminEventPublisher.TOPIC;
  }

  @Override
  public void deliver(Map<String, Object> payload) {
    Object name = payload.get("model");
    Optional<Model> model = name == null ? Optional.empty() : registry.find(String.valueOf(name));
    if (!targets.sendsFor(model)) {
      return;
    }
    Optional<Target> target = targets.targetFor(model);
    if (target.isEmpty()) {
      throw new IllegalStateException(
          "The webhook target " + model.map(Model::webhook).orElse(null) + " is not configured");
    }
    sender.send(target.get(), String.valueOf(payload.get("id")), payload, ChatText.admin(payload));
  }
}
