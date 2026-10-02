package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.event.DiagnosisPendingEvent;
import HaUI.ImplSolution.DX_Farm.event.DiseaseReportCreatedEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Builds Mattermost attachment payloads. User-provided strings are shortened, stripped of
// line breaks, and stop being mentions, links and markdown before they reach the channel.
@Component
public class MattermostMessageBuilder {

  private static final int MAX_VALUE_LENGTH = 100;
  private static final String COLOR_REPORT = "#F2A900";
  private static final String COLOR_DIAGNOSIS = "#1C7ED6";
  private static final String COLOR_REVIEW_REQUIRED = "#E03131";
  private static final String NO_RULE_TEXT = "Không có phác đồ — cần kỹ sư xem xét";
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

  private final String imageBaseUrl;

  public MattermostMessageBuilder(
      @Value("${mattermost.webhook.image-base-url:}") String imageBaseUrl) {
    this.imageBaseUrl = imageBaseUrl == null ? "" : imageBaseUrl.strip().replaceAll("/+$", "");
  }

  public Map<String, Object> reportCreated(DiseaseReportCreatedEvent event) {
    String title = "Báo cáo sâu bệnh mới";
    String fallback = "%s: báo cáo #%d, thửa %s, giống %s, người báo cáo %s, lúc %s".formatted(
        title, event.reportId(), plain(event.parcelCode()), plain(event.riceVariety()),
        plain(event.reportedBy()), formatTime(event.reportedAt()));
    return payload(fallback, COLOR_REPORT, title,
        List.of(field("Mã báo cáo", "#" + event.reportId()),
            field("Mã thửa", markdown(event.parcelCode())),
            field("Giống lúa", markdown(event.riceVariety())),
            field("Người báo cáo", markdown(event.reportedBy())),
            field("Thời điểm", formatTime(event.reportedAt()))),
        imageUrl(event.imageRef()));
  }

  public Map<String, Object> diagnosisPending(DiagnosisPendingEvent event) {
    String title = "Chẩn đoán chờ duyệt";
    String confidence = formatConfidence(event.confidence());
    String rule = event.hasTreatmentRule() ? "Có phác đồ" : NO_RULE_TEXT;
    String fallback = "%s: báo cáo #%d, thửa %s, bệnh %s (%s), độ tin cậy %s. %s".formatted(title,
        event.reportId(), plain(event.parcelCode()), plain(event.diseaseCode()),
        plain(event.diseaseName()), confidence, rule);
    return payload(fallback, event.hasTreatmentRule() ? COLOR_DIAGNOSIS : COLOR_REVIEW_REQUIRED,
        title,
        List.of(field("Mã báo cáo", "#" + event.reportId()),
            field("Mã thửa", markdown(event.parcelCode())),
            field("Bệnh", markdown(event.diseaseCode()) + " — " + markdown(event.diseaseName())),
            field("Độ tin cậy", confidence), field("Phác đồ", rule)),
        null);
  }

  private String imageUrl(String imageRef) {
    if (imageBaseUrl.isEmpty() || imageRef == null || imageRef.isBlank()) {
      return null;
    }
    return imageBaseUrl + "/uploads/" + URLEncoder.encode(imageRef, StandardCharsets.UTF_8);
  }

  static String plain(String value) {
    if (value == null || value.isBlank()) {
      return "-";
    }
    String text = value.replaceAll("\\s+", " ").strip();
    if (text.codePointCount(0, text.length()) > MAX_VALUE_LENGTH) {
      text = text.substring(0, text.offsetByCodePoints(0, MAX_VALUE_LENGTH)) + "…";
    }
    return text.replace("@", "@​").replace("://", ":​//");
  }

  static String markdown(String value) {
    return plain(value).replaceAll("([\\\\`*_\\[\\]<>|#~])", "\\\\$1");
  }

  static String formatConfidence(BigDecimal confidence) {
    if (confidence == null) {
      return "-";
    }
    return confidence.movePointRight(2).setScale(0, RoundingMode.HALF_UP) + "%";
  }

  private static String formatTime(Instant instant) {
    return instant == null ? "-" : TIME_FORMAT.format(instant);
  }

  private static Map<String, Object> field(String title, String value) {
    return Map.of("short", true, "title", title, "value", value);
  }

  private static Map<String, Object> payload(String fallback, String color, String title,
      List<Map<String, Object>> fields, String imageUrl) {
    Map<String, Object> attachment = new LinkedHashMap<>();
    attachment.put("fallback", fallback);
    attachment.put("color", color);
    attachment.put("title", title);
    attachment.put("fields", fields);
    if (imageUrl != null) {
      attachment.put("image_url", imageUrl);
    }
    return Map.of("attachments", List.of(attachment));
  }
}
