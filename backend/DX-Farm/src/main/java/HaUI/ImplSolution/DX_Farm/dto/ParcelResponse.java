package HaUI.ImplSolution.DX_Farm.dto;

import java.math.BigDecimal;
import java.time.Instant;

// @formatter:off
public record ParcelResponse(
  // Primary key
  Long id,

  // Mã thửa ruộng
  String code,

  // Mã hộ sở hữu thửa
  String householdCode,

  // Tên chủ hộ/nông dân
  String farmerName,

  // Parcel area in hectares
  BigDecimal areaHectares,

  // Parcel location or address
  String location,

  // GPS position
  BigDecimal latitude,

  BigDecimal longitude,

  // Error radius of the GPS fix in meters; null when an admin entered the position
  BigDecimal gpsAccuracyMeters,

  // Older parcel without a position
  boolean locationMissing,

  // PENDING, APPROVED or REJECTED
  String approvalStatus,

  // Reason it was rejected
  String rejectionReason,

  // Thời điểm tạo
  Instant createdAt,

  // Thời điểm cập nhật gần nhất
  Instant updatedAt
){}
