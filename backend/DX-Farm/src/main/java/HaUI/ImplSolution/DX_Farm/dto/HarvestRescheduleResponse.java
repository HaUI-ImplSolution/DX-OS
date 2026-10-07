// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.dto;

import java.time.Instant;
import java.time.LocalDate;

// @formatter:off
public record HarvestRescheduleResponse(
  Long id,
  LocalDate previousDate,
  LocalDate newDate,
  String reason,
  String rescheduledByName,
  Instant rescheduledAt
){}
