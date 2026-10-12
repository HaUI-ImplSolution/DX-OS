// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// @formatter:off
public record HarvestRescheduleRequest(
  // Today or later, at most 90 days ahead
  @NotNull
  LocalDate newExpectedHarvestDate,

  @NotBlank
  @Size(max = 255)
  String reason
){}
