// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.dto;

import java.time.LocalDate;
import java.util.List;

// What the farmer should know before declaring a harvest.
// @formatter:off
public record HarvestReadinessResponse(
  Long cropSeasonId,

  LocalDate expectedHarvestDate,

  // Days between today and the expected date; 0 when today is the day or it has passed
  long daysBeforeExpected,

  // Declaring today needs confirmEarly and a reason
  boolean earlyConfirmationRequired,

  // Pesticide isolation, same answer as the isolation-status endpoint
  IsolationStatusResponse isolation,

  // Open work that does not block the harvest
  List<HarvestWarning> warnings
){}
