// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.engine.event.WebhookTargets.Target;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Action;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Asks the service an action names with check whether the action may be taken. The service gets
// the record and what is about to happen and answers {"allow": true} or {"allow": false, "code":
// ..., "message": ...} with a 2xx status. X-Dxos-Event-Id is the same for every try of the same
// action on the same version of a record, so the service can drop a repeat. When the service
// cannot be asked or answers wrongly, the on_error of its target decides; bypass skips the ask.
@Component
public class ActionCheck {

  public static final String DENIED = "check_denied";
  public static final String UNAVAILABLE = "check_unavailable";
  public static final String SKIPPED = "check_skipped";

  private static final Duration TIMEOUT = Duration.ofSeconds(3);
  private static final int MESSAGE_LIMIT = 300;

  // history is the line to write in the history of the record, null when the action goes on
  // without one. A denied or unavailable verdict stops the action; code and message are what the
  // service gave, detail says why a check was skipped or unavailable.
  public record Verdict(String history, boolean stops, String code, String message, String detail) {

    static Verdict allowed() {
      return new Verdict(null, false, null, null, null);
    }
  }

  private final WebhookTargets targets;
  private final WebhookSender sender;
  private final ObjectMapper json;

  public ActionCheck(WebhookTargets targets, WebhookSender sender, ObjectMapper json) {
    this.targets = targets;
    this.sender = sender;
    this.json = json;
  }

  public Verdict ask(Action action, String eventId, Map<String, Object> message) {
    Optional<Target> found = targets.checkTarget(action.check());
    if (found.isPresent() && found.get().bypass()) {
      return new Verdict(SKIPPED, false, null, null, "bypass");
    }
    try {
      if (found.isEmpty()) {
        throw new IllegalStateException("The check target is not configured");
      }
      JsonNode answer = json.readTree(sender.ask(found.get(), eventId, message, TIMEOUT));
      if (answer == null || !answer.path("allow").isBoolean()) {
        throw new IllegalStateException("The service did not say allow true or false");
      }
      if (answer.path("allow").asBoolean()) {
        return Verdict.allowed();
      }
      return new Verdict(
          DENIED,
          true,
          clip(answer.path("code").asString("")),
          clip(answer.path("message").asString("")),
          null);
    } catch (RuntimeException e) {
      String reason = e.getClass().getSimpleName();
      boolean allow = found.isPresent() && found.get().allowOnError();
      return new Verdict(allow ? SKIPPED : UNAVAILABLE, !allow, null, null, reason);
    }
  }

  // The body the service is sent.
  public static Map<String, Object> body(
      String model,
      String entity,
      long id,
      Action action,
      String from,
      String actor,
      String note,
      Map<String, Object> record) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("entity", entity);
    body.put("record_id", id);
    body.put("action", action.name());
    body.put("from", from);
    body.put("to", action.to());
    body.put("actor", actor);
    body.put("note", note);
    body.put("record", record);
    return body;
  }

  private static String clip(String text) {
    return text.length() > MESSAGE_LIMIT ? text.substring(0, MESSAGE_LIMIT) : text;
  }
}
