package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// @formatter:off
public record ParcelCreateRequest(
  @NotBlank
  String code,

  @NotBlank
  String farmerName,

  @NotNull
  @DecimalMin(value="0.01")
  BigDecimal areaHectares,

  String location
){}
