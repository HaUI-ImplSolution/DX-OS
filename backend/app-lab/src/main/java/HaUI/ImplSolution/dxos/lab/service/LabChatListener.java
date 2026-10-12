// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.notification.ChatWebhook;
import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import HaUI.ImplSolution.dxos.lab.LabNotificationConfig;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Posts to the chat channels after the publishing transaction commits. Runs on a separate executor
// and never propagates an exception back to the business flow.
@Component
public class LabChatListener {

  private static final Logger log = LoggerFactory.getLogger(LabChatListener.class);
  private static final int MAX_VALUE_LENGTH = 100;
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(BusinessTime.ZONE);

  private final ChatWebhook chatWebhook;

  public LabChatListener(ChatWebhook chatWebhook) {
    this.chatWebhook = chatWebhook;
  }

  @Async("mattermostNotifyExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTicketCreated(LabEvents.TicketCreated event) {
    dispatch(LabNotificationConfig.TECHNICIAN_CHANNEL,
        () -> payload("#2F9E44", "Yêu cầu hỗ trợ mới",
            "%s: %s, loại %s, ưu tiên %s, người gửi %s".formatted(plain(event.code()),
                plain(event.title()), plain(event.categoryName()), event.priority(),
                plain(event.requesterName())),
            field("Mã", plain(event.code())), field("Tiêu đề", plain(event.title())),
            field("Loại", plain(event.categoryName())), field("Ưu tiên", event.priority()),
            field("Người gửi", plain(event.requesterName())),
            field("Thời điểm", TIME_FORMAT.format(event.createdAt()))));
  }

  @Async("mattermostNotifyExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTicketOverdue(LabEvents.TicketOverdue event) {
    Supplier<Map<String, Object>> message = () -> payload("#E03131", "Yêu cầu quá hạn xử lý",
        "%s: %s, ưu tiên %s, người xử lý %s, hạn %s".formatted(plain(event.code()),
            plain(event.title()), event.priority(),
            event.assigneeName() == null ? "chưa có" : plain(event.assigneeName()),
            TIME_FORMAT.format(event.dueAt())),
        field("Mã", plain(event.code())), field("Tiêu đề", plain(event.title())),
        field("Ưu tiên", event.priority()),
        field("Người xử lý",
            event.assigneeName() == null ? "Chưa có" : plain(event.assigneeName())),
        field("Hạn xử lý", TIME_FORMAT.format(event.dueAt())));
    dispatch(LabNotificationConfig.TECHNICIAN_CHANNEL, message);
    dispatch(LabNotificationConfig.ADMIN_CHANNEL, message);
  }

  private void dispatch(String channel, Supplier<Map<String, Object>> payload) {
    if (!chatWebhook.isEnabled(channel)) {
      return;
    }
    try {
      chatWebhook.send(channel, payload.get());
    } catch (Exception e) {
      log.warn("Chat notification failed: {}", e.getClass().getSimpleName());
    }
  }

  private static Map<String, Object> payload(String color, String title, String fallback,
      Map<String, Object>... fields) {
    Map<String, Object> attachment = new LinkedHashMap<>();
    attachment.put("fallback", fallback);
    attachment.put("color", color);
    attachment.put("title", title);
    attachment.put("fields", List.of(fields));
    return Map.of("attachments", List.of(attachment));
  }

  private static Map<String, Object> field(String title, String value) {
    return Map.of("title", title, "value", value, "short", true);
  }

  // User text is shortened and loses line breaks, mentions and markdown before it reaches a
  // channel.
  private static String plain(String value) {
    if (value == null) {
      return "";
    }
    String text = value.replaceAll("[\\r\\n]+", " ").replaceAll("[@#`*_~\\[\\]<>|]", "").strip();
    return text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) + "…" : text;
  }
}
