package HaUI.ImplSolution.DX_Farm.dto;

import java.time.Instant;
import java.time.LocalDate;

// @formatter:off
public record CropSeasonResponse(
  // Primary key
  Long id,

  // Crop season code (e.g. HTX01-H0042-T03-2026DX); absent on older seasons
  String code,

  // Mã thửa ruộng sở hữu vụ mùa
  String parcelCode,

  // Giống lúa gieo trồng
  String riceVariety,

  // Catalogue variety code; absent on older seasons
  String riceVarietyCode,

  // DONG_XUAN, HE_THU or THU_DONG; absent on older seasons
  String seasonType,

  // Ngày bắt đầu vụ
  LocalDate startDate,

  // Ngày dự kiến thu hoạch
  LocalDate expectedHarvestDate,

  // Reason given when the expected date is outside the growth range
  String reasonForOverride,

  // Trạng thái vụ mùa (ACTIVE/HARVESTED)
  String status,

  // PENDING, APPROVED or REJECTED
  String approvalStatus,

  // Reason it was rejected
  String rejectionReason,

  // Thời điểm thực tế đã thu hoạch
  Instant harvestedAt
){}
