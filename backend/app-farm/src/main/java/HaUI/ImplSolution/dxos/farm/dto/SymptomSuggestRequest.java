// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// @formatter:off
public record SymptomSuggestRequest(
  // Free-text description of what the engineer sees on the plant
  @NotBlank @Size(max = 4000) String symptomText
) {}
