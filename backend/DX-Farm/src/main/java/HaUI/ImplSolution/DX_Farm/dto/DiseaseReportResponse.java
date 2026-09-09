package HaUI.ImplSolution.DX_Farm.dto;

import java.time.Instant;

// @formatter:off
public record DiseaseReportResponse(
  // Khoá chính
  Long id,

  // Vụ mùa được báo cáo sự cố
  Long cropSeasonId,

  // Tên/đường dẫn file ảnh đã upload
  String imageRef,

  // Tên người báo cáo (nông dân)
  String reportedBy,

  // Thời điểm báo cáo được ghi nhận
  Instant reportedAt,

  // Chẩn đoán đi kèm báo cáo này (null nếu chưa chẩn đoán)
  DiagnosisResponse diagnosis
){}
