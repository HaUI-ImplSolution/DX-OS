package HaUI.ImplSolution.DX_Farm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// @formatter:off
public record TreatmentApplicationResponse(
  // Khoá chính
  Long id,

  // Vụ mùa được áp dụng xử lý
  Long cropSeasonId,

  // Chẩn đoán gốc dẫn tới lần phun này (null nếu ghi nhận thủ công)
  Long diagnosisId,

  // Mã thuốc thực tế đã phun
  String pesticideCode,

  // Liều lượng đã phun, snapshot tại thời điểm áp dụng
  BigDecimal doseAmountSnapshot,

  // Đơn vị liều lượng, snapshot tại thời điểm áp dụng
  String doseUnitSnapshot,

  // Số ngày cách ly, snapshot tại thời điểm áp dụng
  Integer phiDaysSnapshot,

  // Ngày thực tế đã phun thuốc
  LocalDate appliedAt,

  // Ngày được phép thu hoạch trở lại
  LocalDate harvestLockedUntil
){}
