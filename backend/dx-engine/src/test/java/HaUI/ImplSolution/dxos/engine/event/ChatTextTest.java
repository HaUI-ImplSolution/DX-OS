// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatTextTest {

  private static Map<String, Object> message(String action) {
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("action", action);
    message.put("model", "shop");
    message.put("entity", "order");
    message.put("recordId", 7);
    message.put("actorName", "Ann");
    message.put("fromStatus", "submitted");
    message.put("toStatus", "approved");
    message.put("level", 2);
    return message;
  }

  @Test
  void aNewRecordOfAProcessIsANewTaskForItsHandler() {
    assertEquals(
        "Việc mới: shop/order #7, trạng thái approved (Ann)",
        ChatText.record(message("created"), true));
    assertEquals(
        "Việc mới: shop/order #7, trạng thái approved (Ann)",
        ChatText.record(message("resubmit"), true));
  }

  @Test
  void aRecordWithoutAProcessIsOnlyReportedAsWhatHappened() {
    assertEquals(
        "shop/order #7: created, submitted → approved (Ann)",
        ChatText.record(message("created"), false));
  }

  @Test
  void deadlinesAndEscalationsNameTheRecordAndItsStatus() {
    assertEquals(
        "Sắp đến hạn xử lý: shop/order #7, trạng thái approved",
        ChatText.record(message("due_soon"), true));
    assertEquals(
        "Quá hạn xử lý: shop/order #7, trạng thái approved",
        ChatText.record(message("overdue"), true));
    assertEquals(
        "Leo thang mức 2: shop/order #7 quá hạn xử lý, trạng thái approved",
        ChatText.record(message("escalated"), true));
  }

  @Test
  void anAssignmentNamesWhoDidIt() {
    assertEquals("Được giao: shop/order #7 (Ann)", ChatText.record(message("assigned"), true));
  }

  @Test
  void anotherActionIsWrittenWithItsNameAndTheStatusesItMoved() {
    assertEquals(
        "shop/order #7: approve, submitted → approved (Ann)",
        ChatText.record(message("approve"), true));
  }

  @Test
  void theTextNeverHoldsTheContentOfARecord() {
    Map<String, Object> message = message("approve");
    message.put("note", "secret reason");
    message.put("detail", "secret detail");

    assertFalse(ChatText.record(message, true).contains("secret"));
  }

  @Test
  void anAdministratorChangeListsItsFacts() {
    Map<String, Object> admin = new LinkedHashMap<>();
    admin.put("kind", "apply");
    admin.put("model", "shop");
    admin.put("actorName", "Root");
    admin.put("facts", Map.of("version", 3));

    assertEquals("Quản trị: apply [shop] bởi Root (version=3)", ChatText.admin(admin));
  }

  @Test
  void anAdministratorChangeWithoutAModelOrFactsIsShort() {
    Map<String, Object> admin = new LinkedHashMap<>();
    admin.put("kind", "user_created");
    admin.put("actorName", "Root");

    assertEquals("Quản trị: user_created bởi Root", ChatText.admin(admin));
  }
}
