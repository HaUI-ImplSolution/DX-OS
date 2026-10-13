// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.controller;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.lab.dto.StatsDtos.LabStatsResponse;
import HaUI.ImplSolution.dxos.lab.service.LabStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lab-stats")
@Tag(name = "LabStats", description = "Lab statistics - Data")
@PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
public class LabStatsController {

  private final LabStatsService statsService;

  public LabStatsController(LabStatsService statsService) {
    this.statsService = statsService;
  }

  @GetMapping("/summary")
  @Operation(summary = "Tickets, SLA, technicians, room usage and devices for a range of days",
      description = "TECHNICIAN or ADMIN only. from and to are yyyy-MM-dd and inclusive; to"
          + " defaults to today and from to 29 days before to (at most 366 days, 400 otherwise)."
          + " Ticket figures cover tickets created in the range. Averages are in minutes, rates"
          + " between 0 and 1 and null when nothing qualifies.")
  public ApiResponse<LabStatsResponse> summary(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ApiResponse.success(statsService.summary(from, to));
  }
}
