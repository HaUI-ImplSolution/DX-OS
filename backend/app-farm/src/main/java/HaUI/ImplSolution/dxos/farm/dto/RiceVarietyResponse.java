// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

// @formatter:off
public record RiceVarietyResponse(
  // Variety code (e.g. OM5451)
  String code,

  String name,

  // Days from sowing to harvest, shortest and longest
  int growthDaysMin,

  int growthDaysMax,

  // Source of the figures
  String source,

  // APPROVED or IN_REVIEW
  String reviewStatus
){}
