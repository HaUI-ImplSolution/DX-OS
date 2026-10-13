// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

import java.time.LocalDate;

// @formatter:off
public record CropSeasonSuggestionResponse(
  // Season type suggested by the start month; null when the month matches no season
  String seasonType,

  // Suggested harvest date: the middle of the growth range
  LocalDate expectedHarvestDate,

  // Harvest dates that need no reason
  LocalDate earliestHarvestDate,

  LocalDate latestHarvestDate
){}
