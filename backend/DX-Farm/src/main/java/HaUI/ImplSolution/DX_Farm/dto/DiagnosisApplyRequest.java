package HaUI.ImplSolution.DX_Farm.dto;

import java.time.LocalDate;

// @formatter:off
public record DiagnosisApplyRequest(
  // Ngày thực tế đã phun thuốc (mặc định hôm nay nếu để trống)
  LocalDate appliedAt
){}
