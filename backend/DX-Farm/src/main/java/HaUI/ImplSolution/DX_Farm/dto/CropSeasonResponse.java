package HaUI.ImplSolution.DX_Farm.dto;

import java.time.Instant;
import java.time.LocalDate;

// @formatter:off
public record CropSeasonResponse(
  // Khoá chính
  Long id,

  // Mã thửa ruộng sở hữu vụ mùa
  String parcelCode,

  // Giống lúa gieo trồng
  String riceVariety,

  // Ngày bắt đầu vụ
  LocalDate startDate,

  // Ngày dự kiến thu hoạch
  LocalDate expectedHarvestDate,

  // Trạng thái vụ mùa (ACTIVE/HARVESTED)
  String status,

  // Thời điểm thực tế đã thu hoạch
  Instant harvestedAt
){}
