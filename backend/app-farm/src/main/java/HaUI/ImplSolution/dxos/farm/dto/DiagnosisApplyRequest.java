// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

// @formatter:off
public record DiagnosisApplyRequest(
  @NotNull
  @DecimalMin(value = "0.0", inclusive = false)
  BigDecimal doseAmount,

  LocalDate appliedAt
){}
