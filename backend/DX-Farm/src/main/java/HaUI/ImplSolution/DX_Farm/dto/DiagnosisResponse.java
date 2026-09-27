package HaUI.ImplSolution.DX_Farm.dto;

import java.math.BigDecimal;
import java.time.Instant;

// @formatter:off
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

  // Khuyến nghị dạng văn bản sinh ra từ quy tắc xử lý
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
  TreatmentRuleResponse treatmentRule
){}
