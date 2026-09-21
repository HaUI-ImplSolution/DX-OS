// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

// @formatter:off
public record HarvestWarning(
  // PENDING_REPORTS or UNTREATED_RECOMMENDATIONS
  String code,

  // Vietnamese sentence shown to the farmer
  String message
){}
