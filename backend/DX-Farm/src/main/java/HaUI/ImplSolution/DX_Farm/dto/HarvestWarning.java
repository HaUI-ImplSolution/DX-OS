// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.dto;

// @formatter:off
public record HarvestWarning(
  // PENDING_REPORTS or UNTREATED_RECOMMENDATIONS
  String code,

  // Vietnamese sentence shown to the farmer
  String message
){}
