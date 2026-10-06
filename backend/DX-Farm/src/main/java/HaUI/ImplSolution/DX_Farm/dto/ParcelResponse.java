package HaUI.ImplSolution.DX_Farm.dto;

import java.math.BigDecimal;
import java.time.Instant;

// @formatter:off
public record ParcelResponse(
  // Khoá chính
  Long id,

  // Mã thửa ruộng
  String code,

  // Mã hộ sở hữu thửa
  String householdCode,

  // Tên chủ hộ/nông dân
  String farmerName,

  // Diện tích thửa (hecta)
  BigDecimal areaHectares,

  // Vị trí/địa chỉ thửa ruộng
  String location,

  // Toạ độ GPS (tuỳ chọn)
  BigDecimal latitude,

  BigDecimal longitude,

  // Thời điểm tạo
  Instant createdAt,

  // Thời điểm cập nhật gần nhất
  Instant updatedAt
){}
