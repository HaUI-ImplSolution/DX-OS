// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.farm.event.CropSeasonCreatedEvent;
import HaUI.ImplSolution.dxos.farm.event.DiagnosisDecidedEvent;
import HaUI.ImplSolution.dxos.farm.event.DiagnosisPendingEvent;
import HaUI.ImplSolution.dxos.farm.event.DiseaseReportCreatedEvent;
import HaUI.ImplSolution.dxos.farm.event.ParcelCreatedEvent;
import HaUI.ImplSolution.dxos.farm.event.TreatmentConfirmationOverdueEvent;
import HaUI.ImplSolution.dxos.farm.event.TreatmentDecidedEvent;
import HaUI.ImplSolution.dxos.farm.event.TreatmentReportedEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
  private static final String COLOR_REQUEST = "#2F9E44";
  private static final String COLOR_REVIEW_REQUIRED = "#E03131";
  private static final String NO_RULE_TEXT = "Không có phác đồ — cần kỹ sư xem xét";
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  // Chat history keeps the message, so the photo link lives as long as a presigned URL can.
  private static final Duration IMAGE_URL_TTL = Duration.ofDays(7);

  private final ObjectStorage storageService;
  private final String imageBaseUrl;
  private final String appBaseUrl;

  public MattermostMessageBuilder(ObjectStorage storageService,
      @Value("${mattermost.webhook.image-base-url:}") String imageBaseUrl,
      @Value("${mattermost.webhook.app-base-url:}") String appBaseUrl) {
    this.storageService = storageService;
    this.imageBaseUrl = trimUrl(imageBaseUrl);
    this.appBaseUrl = trimUrl(appBaseUrl);
  }

  private static String trimUrl(String url) {
    return url == null ? "" : url.strip().replaceAll("/+$", "");
  }

  // Opens the report in the web app; empty when the app address is not configured.
  private String reportLink(Long reportId) {
    return appBaseUrl.isEmpty() ? null : appBaseUrl + "/?report=" + reportId;
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
        imageUrl(event.imageRef()), reportLink(event.reportId()));
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
        null, reportLink(event.reportId()));
  }

  public Map<String, Object> treatmentReported(TreatmentReportedEvent event) {
    String title = "Nông dân khai đã phun thuốc, chờ xác nhận";
    String dose = event.doseAmount().stripTrailingZeros().toPlainString() + " " + event.doseUnit();
    String fallback = "%s: báo cáo #%d, thửa %s, thuốc %s, liều %s, ngày phun %s".formatted(title,
        event.reportId(), plain(event.parcelCode()), plain(event.pesticideName()), plain(dose),
        formatDate(event.appliedAt()));
    return payload(fallback, COLOR_REVIEW_REQUIRED, title,
        List.of(field("Mã báo cáo", "#" + event.reportId()),
            field("Mã thửa", markdown(event.parcelCode())),
            field("Chủ hộ", markdown(event.farmerName())),
            field("Thuốc", markdown(event.pesticideName())), field("Liều", markdown(dose)),
            field("Ngày phun", formatDate(event.appliedAt())),
            field("Hết cách ly", formatDate(event.harvestLockedUntil()))),
        null, reportLink(event.reportId()));
  }

  public Map<String, Object> treatmentConfirmationOverdue(TreatmentConfirmationOverdueEvent event) {
    String title = "Bản khai phun thuốc chờ xác nhận quá lâu";
    String fallback = "%s: báo cáo #%d, thửa %s, thuốc %s, khai lúc %s".formatted(title,
        event.reportId(), plain(event.parcelCode()), plain(event.pesticideName()),
        formatTime(event.reportedAt()));
    return payload(fallback, COLOR_REVIEW_REQUIRED, title,
        List.of(field("Mã báo cáo", "#" + event.reportId()),
            field("Mã thửa", markdown(event.parcelCode())),
            field("Chủ hộ", markdown(event.farmerName())),
            field("Thuốc", markdown(event.pesticideName())),
            field("Khai lúc", formatTime(event.reportedAt()))),
        null, reportLink(event.reportId()));
  }

  public Map<String, Object> diagnosisDecided(DiagnosisDecidedEvent event) {
    String title = event.approved() ? "Chẩn đoán đã được duyệt" : "Chẩn đoán bị từ chối";
    List<Map<String, Object>> fields =
        new ArrayList<>(List.of(field("Mã báo cáo", "#" + event.reportId()),
            field("Mã thửa", markdown(event.parcelCode())),
            field("Bệnh", markdown(event.diseaseName())),
            field("Người xử lý", markdown(event.decidedBy()))));
    if (!event.approved()) {
      fields.add(field("Lý do", markdown(event.reason())));
    }
    String fallback = "%s: báo cáo #%d, thửa %s, bệnh %s, bởi %s%s".formatted(title,
        event.reportId(), plain(event.parcelCode()), plain(event.diseaseName()),
        plain(event.decidedBy()), event.approved() ? "" : ". Lý do: " + plain(event.reason()));
    return payload(fallback, event.approved() ? COLOR_REQUEST : COLOR_REVIEW_REQUIRED, title,
        fields, null, reportLink(event.reportId()));
  }

  public Map<String, Object> treatmentDecided(TreatmentDecidedEvent event) {
    String title = switch (event.outcome()) {
      case RECORDED -> "Đã ghi nhận việc phun thuốc";
      case CONFIRMED -> "Đã xác nhận việc phun thuốc";
      case REJECTED -> "Đã từ chối khai phun thuốc";
    };
    List<Map<String, Object>> fields = new ArrayList<>(
        List.of(field("Mã báo cáo", "#" + event.reportId()),
            field("Mã thửa", markdown(event.parcelCode())),
            field("Thuốc", markdown(event.pesticideName())),
            field("Người xử lý", markdown(event.decidedBy()))));
    if (event.outcome() == TreatmentDecidedEvent.Outcome.REJECTED) {
      fields.add(field("Lý do", markdown(event.reason())));
    } else {
      fields.add(field("Hết cách ly", formatDate(event.harvestLockedUntil())));
    }
    String fallback = "%s: báo cáo #%d, thửa %s, thuốc %s, bởi %s".formatted(title,
        event.reportId(), plain(event.parcelCode()), plain(event.pesticideName()),
        plain(event.decidedBy()));
    return payload(fallback,
        event.outcome() == TreatmentDecidedEvent.Outcome.REJECTED ? COLOR_REVIEW_REQUIRED
            : COLOR_REQUEST,
        title, fields, null, reportLink(event.reportId()));
  }

  public Map<String, Object> parcelCreated(ParcelCreatedEvent event) {
    String title = event.resubmitted() ? "Thửa ruộng gửi lại chờ duyệt" : "Yêu cầu thửa ruộng mới";
    String fallback =
        "%s: thửa %s, hộ %s (%s), diện tích %s ha".formatted(title, plain(event.parcelCode()),
            plain(event.householdCode()), plain(event.farmerName()), event.areaHectares());
    return payload(fallback, COLOR_REQUEST, title,
        List.of(field("Mã thửa", markdown(event.parcelCode())),
            field("Mã hộ", markdown(event.householdCode())),
            field("Chủ hộ", markdown(event.farmerName())),
            field("Diện tích", event.areaHectares() + " ha"),
            field("Vị trí", event.latitude() + ", " + event.longitude())),
        null);
  }

  public Map<String, Object> cropSeasonCreated(CropSeasonCreatedEvent event) {
    String title = event.resubmitted() ? "Vụ mùa gửi lại chờ duyệt" : "Yêu cầu vụ mùa mới";
    String fallback =
        "%s: vụ %s, thửa %s, giống %s, bắt đầu %s".formatted(title, plain(event.seasonCode()),
            plain(event.parcelCode()), plain(event.riceVariety()), formatDate(event.startDate()));
    return payload(fallback, COLOR_REQUEST, title,
        List.of(field("Mã vụ", markdown(event.seasonCode())),
            field("Mã thửa", markdown(event.parcelCode())),
            field("Chủ hộ", markdown(event.farmerName())),
            field("Giống lúa", markdown(event.riceVariety())),
            field("Bắt đầu", formatDate(event.startDate())),
            field("Dự kiến thu hoạch", formatDate(event.expectedHarvestDate()))),
        null);
  }

  private static String formatDate(LocalDate date) {
    return date == null ? "-" : DATE_FORMAT.format(date);
  }

  private String imageUrl(String imageRef) {
    if (imageBaseUrl.isEmpty() || imageRef == null || imageRef.isBlank()) {
      return null;
    }
    return storageService.presignedUrl(imageBaseUrl, imageRef, IMAGE_URL_TTL);
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
    return payload(fallback, color, title, fields, imageUrl, null);
  }

  private static Map<String, Object> payload(String fallback, String color, String title,
      List<Map<String, Object>> fields, String imageUrl, String titleLink) {
    Map<String, Object> attachment = new LinkedHashMap<>();
    attachment.put("fallback", fallback);
    attachment.put("color", color);
    attachment.put("title", title);
    if (titleLink != null) {
      attachment.put("title_link", titleLink);
    }
    attachment.put("fields", fields);
    if (imageUrl != null) {
      attachment.put("image_url", imageUrl);
    }
    return Map.of("attachments", List.of(attachment));
  }
}
