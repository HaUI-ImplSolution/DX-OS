// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import java.util.Map;
import java.util.StringJoiner;

// The sentence a chat target shows for a message. It holds what the message holds: names, ids and
// statuses, never the content of a record.
final class ChatText {

  private ChatText() {}

  // A line of the history of a record. A record of an entity with a process that is created or
  // submitted again is a new task for its handler.
  static String record(Map<String, Object> message, boolean hasProcess) {
    String action = String.valueOf(message.get("action"));
    String what =
        message.get("model") + "/" + message.get("entity") + " #" + message.get("recordId");
    String by = " (" + message.get("actorName") + ")";
    String statuses = message.get("fromStatus") + " → " + message.get("toStatus");
    if (hasProcess && (action.equals("created") || action.equals("resubmit"))) {
      return "Việc mới: " + what + ", trạng thái " + message.get("toStatus") + by;
    }
    return switch (action) {
      case "due_soon" -> "Sắp đến hạn xử lý: " + what + ", trạng thái " + message.get("toStatus");
      case "overdue" -> "Quá hạn xử lý: " + what + ", trạng thái " + message.get("toStatus");
      case "escalated" ->
          "Leo thang mức "
              + message.get("level")
              + ": "
              + what
              + " quá hạn xử lý, trạng thái "
              + message.get("toStatus");
      case "assigned" -> "Được giao: " + what + by;
      default -> what + ": " + action + ", " + statuses + by;
    };
  }

  // A change made by an administrator: an apply of a model or a change to an account.
  static String admin(Map<String, Object> message) {
    StringJoiner facts = new StringJoiner(", ");
    if (message.get("facts") instanceof Map<?, ?> map) {
      map.forEach((key, value) -> facts.add(key + "=" + value));
    }
    String model = message.get("model") == null ? "" : " [" + message.get("model") + "]";
    return "Quản trị: "
        + message.get("kind")
        + model
        + " bởi "
        + message.get("actorName")
        + (facts.length() == 0 ? "" : " (" + facts + ")");
  }
}
