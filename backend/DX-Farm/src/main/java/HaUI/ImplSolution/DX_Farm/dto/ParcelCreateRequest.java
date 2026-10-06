package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// @formatter:off
public record ParcelCreateRequest(
  // Mã hộ sở hữu thửa; bắt buộc với ADMIN, bị bỏ qua với FARMER (luôn dùng hộ của chính mình)
  String householdCode,

  // Parcel area in hectares
  @NotNull
  @DecimalMin(value="0.01")
  BigDecimal areaHectares,

  // Parcel location or address
  String location,

  // GPS position, required
  @NotNull @DecimalMin(value = "-90") @DecimalMax(value = "90")
  BigDecimal latitude,

  @NotNull @DecimalMin(value = "-180") @DecimalMax(value = "180")
  BigDecimal longitude,

  // Error radius of the GPS fix in meters; required for a FARMER
  @DecimalMin(value = "0")
  BigDecimal gpsAccuracyMeters,

  // The device reports the position as coming from a mock-location app
  Boolean mockedLocation
){}
