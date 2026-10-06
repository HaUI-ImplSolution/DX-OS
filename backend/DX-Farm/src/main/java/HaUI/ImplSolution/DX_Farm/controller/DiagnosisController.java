package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisApplyRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisCreateRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisRejectRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisResponse;
import HaUI.ImplSolution.DX_Farm.dto.TreatmentApplicationResponse;
import HaUI.ImplSolution.DX_Farm.service.DiagnosisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/disease-reports/{reportId}/diagnosis")
@Tag(name = "Diagnosis", description = "Chẩn đoán + duyệt + ghi nhận xử lý")
@PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
public class DiagnosisController {

  private final DiagnosisService diagnosisService;

  public DiagnosisController(DiagnosisService diagnosisService) {
    this.diagnosisService = diagnosisService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Record CV diagnosis manually (testing/demo) + deterministic treatment lookup")
  public ApiResponse<DiagnosisResponse> create(@PathVariable Long reportId,
      @Valid @RequestBody DiagnosisCreateRequest request) {
    return ApiResponse.success("Diagnosis recorded", diagnosisService.create(reportId, request));
  }

  @PostMapping("/ai")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Trigger AI Service diagnosis + deterministic treatment lookup")
  public ApiResponse<DiagnosisResponse> createFromAi(@PathVariable Long reportId) {
    return ApiResponse.success("Diagnosis recorded", diagnosisService.createFromAi(reportId));
  }

  @PostMapping("/approve")
  @Operation(summary = "Engineer approves the recommendation (approvedBy = logged-in engineer)",
      description = "409 if the diagnosis is not pending or was modified concurrently.")
  public ApiResponse<DiagnosisResponse> approve(@PathVariable Long reportId) {
    return ApiResponse.success("Diagnosis approved", diagnosisService.approve(reportId));
  }

  @PostMapping("/reject")
  @Operation(summary = "Engineer rejects the recommendation (rejectedBy = logged-in engineer)",
      description = "409 if the diagnosis is not pending or was modified concurrently.")
  public ApiResponse<DiagnosisResponse> reject(@PathVariable Long reportId,
      @Valid @RequestBody DiagnosisRejectRequest request) {
    return ApiResponse.success("Diagnosis rejected", diagnosisService.reject(reportId, request));
  }

  @PostMapping("/apply")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Record actual treatment dose + compute harvest lock",
      description = "appliedAt defaults to today (Asia/Ho_Chi_Minh); 422 INVALID_APPLIED_AT if it"
          + " is in the future or more than 30 days in the past. 409 if the diagnosis was"
          + " already applied or the crop season was modified concurrently.")
  public ApiResponse<TreatmentApplicationResponse> apply(@PathVariable Long reportId,
      @Valid @RequestBody DiagnosisApplyRequest request) {
    return ApiResponse.success("Treatment application recorded",
        diagnosisService.apply(reportId, request));
  }
}
