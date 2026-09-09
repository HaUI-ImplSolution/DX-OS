package HaUI.ImplSolution.DX_Farm.dto;

import java.math.BigDecimal;

// @formatter:off
public record TreatmentRuleResponse(
  // Khoá chính
  Long id,

  // Mã bệnh áp dụng quy tắc
  String diseaseCode,

  // Mã thuốc khuyến cáo
  String pesticideCode,

  // Liều lượng khuyến cáo
  BigDecimal doseAmount,

  // Đơn vị của liều lượng
  String doseUnit,

  // Số ngày cách ly trước khi thu hoạch (PHI)
  Integer phiDays,

  // Ghi chú thêm về cách dùng
  String notes,

  // Nguồn tham chiếu quy tắc
  String source,

  // Phiên bản quy tắc
  String version
){}
