// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// @formatter:off
@Schema(description = "AI diagnosis of a disease report. For FARMER users, PENDING exposes only"
    + " id, diseaseReportId and approvalStatus; REJECTED adds decidedAt and rejectionReason;"
    + " every other field is null until APPROVED.")
public record DiagnosisResponse(
  // Khoá chính
  Long id,

  // Báo cáo sự cố mà chẩn đoán này thuộc về
  Long diseaseReportId,

  // Mã bệnh được chẩn đoán
  String diseaseCode,

  // Tên bệnh được chẩn đoán
  String diseaseName,

  // Độ tin cậy của chẩn đoán (0.0 - 1.0)
  BigDecimal confidence,

  // Phiên bản model AI đã dùng để chẩn đoán
  String modelVersion,

  // Thời điểm chẩn đoán được ghi nhận
  Instant diagnosedAt,

  // Khuyến nghị dạng văn bản; null với chẩn đoán do AI tạo
  String recommendationText,

  // Trạng thái duyệt (PENDING/APPROVED/REJECTED)
  String approvalStatus,

  // Người đưa ra quyết định duyệt hoặc từ chối
  String decidedBy,

  // Thời điểm đưa ra quyết định duyệt hoặc từ chối
  Instant decidedAt,

  // Lý do từ chối (chỉ có giá trị khi bị REJECTED)
  String rejectionReason,

  // Quy tắc xử lý deterministic tương ứng với bệnh
  TreatmentRuleResponse treatmentRule,

  // Đoạn tài liệu tham khảo AI tìm được, theo thứ tự liên quan; không tham gia quyết định điều trị
  @Schema(description = "Reference passages the AI retrieved, best first; never used to decide the"
      + " pesticide, dose or PHI. Empty for manual and older diagnoses.")
  List<KnowledgeAnswerResponse.Passage> knowledgeEvidence
){}
