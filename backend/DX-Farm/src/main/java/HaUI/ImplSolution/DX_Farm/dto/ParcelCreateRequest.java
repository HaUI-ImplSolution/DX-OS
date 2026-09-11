package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// @formatter:off
public record ParcelCreateRequest(
  // Mã thửa ruộng
  @NotBlank
  String code,

  // Tên chủ hộ/nông dân
  @NotBlank
  String farmerName,

  // Diện tích thửa (hecta)
  @NotNull
  @DecimalMin(value="0.01")
  BigDecimal areaHectares,

  // Vị trí/địa chỉ thửa ruộng
  String location,

  // Toạ độ GPS (tuỳ chọn)
  @DecimalMin(value = "-90") @DecimalMax(value = "90")
  BigDecimal latitude,

  @DecimalMin(value = "-180") @DecimalMax(value = "180")
  BigDecimal longitude
){}
