package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// @formatter:off
public record DiagnosisCreateRequest(
  @NotBlank
  String diseaseCode,

  @NotNull
  @DecimalMin("0.0")
  @DecimalMax("1.0")
  BigDecimal confidence,

  @NotBlank
  String modelVersion
){}
