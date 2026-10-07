// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisApplyRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisRejectRequest;
import HaUI.ImplSolution.DX_Farm.dto.TreatmentApplicationResponse;
import HaUI.ImplSolution.DX_Farm.service.DiagnosisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/disease-reports/{reportId}/treatment-application")
@Tag(name = "TreatmentApplication",
    description = "Spraying reported by the farmer and confirmed by an engineer")
public class TreatmentApplicationController {

  private final DiagnosisService diagnosisService;

  public TreatmentApplicationController(DiagnosisService diagnosisService) {
    this.diagnosisService = diagnosisService;
  }

  @GetMapping
  @Operation(summary = "The spraying recorded for a report",
      description = "FARMER: 404 if the report belongs to another farmer. 404 when nothing was"
          + " recorded yet.")
  public ApiResponse<TreatmentApplicationResponse> get(@PathVariable Long reportId) {
    return ApiResponse.success(diagnosisService.getApplication(reportId));
  }

  @PostMapping("/self-report")
  @PreAuthorize("hasRole('FARMER')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Farmer reports that the approved treatment was sprayed",
      description = "FARMER only; 404 if the report belongs to another farmer. The report waits"
          + " for an engineer (PENDING_CONFIRMATION) and blocks the harvest of the season until"
          + " it is decided. Same dose and appliedAt rules as apply. 409 if the diagnosis is not"
          + " approved, has no treatment rule, or already has a pending or confirmed spraying. A"
          + " rejected report may be reported again.")
  public ApiResponse<TreatmentApplicationResponse> selfReport(@PathVariable Long reportId,
      @Valid @RequestBody DiagnosisApplyRequest request) {
    return ApiResponse.success("Spraying reported", diagnosisService.selfReport(reportId, request));
  }

  @PostMapping("/confirm")
  @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
  @Operation(summary = "Engineer confirms the spraying the farmer reported",
      description = "409 unless the report is waiting for confirmation. The isolation runs from"
          + " the spraying date the farmer gave. The farmer is notified.")
  public ApiResponse<TreatmentApplicationResponse> confirm(@PathVariable Long reportId) {
    return ApiResponse.success("Spraying confirmed", diagnosisService.confirmApplication(reportId));
  }

  @PostMapping("/reject")
  @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
  @Operation(summary = "Engineer does not accept the spraying the farmer reported",
      description = "400 without a reason. 409 unless the report is waiting for confirmation. The"
          + " harvest is no longer blocked by it and the farmer is notified.")
  public ApiResponse<TreatmentApplicationResponse> reject(@PathVariable Long reportId,
      @Valid @RequestBody DiagnosisRejectRequest request) {
    return ApiResponse.success("Spraying rejected",
        diagnosisService.rejectApplication(reportId, request));
  }
}
