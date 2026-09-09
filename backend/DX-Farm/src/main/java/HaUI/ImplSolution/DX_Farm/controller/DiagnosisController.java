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
  @Operation(summary = "Record CV diagnosis + deterministic treatment lookup")
  public ApiResponse<DiagnosisResponse> create(@PathVariable Long reportId,
      @Valid @RequestBody DiagnosisCreateRequest request) {
    return ApiResponse.success("Diagnosis recorded", diagnosisService.create(reportId, request));
  }

  @PostMapping("/approve")
  @Operation(summary = "Engineer approves the recommendation (approvedBy = logged-in engineer)")
  public ApiResponse<DiagnosisResponse> approve(@PathVariable Long reportId) {
    return ApiResponse.success("Diagnosis approved", diagnosisService.approve(reportId));
  }

  @PostMapping("/reject")
  @Operation(summary = "Engineer rejects the recommendation (rejectedBy = logged-in engineer)")
  public ApiResponse<DiagnosisResponse> reject(@PathVariable Long reportId,
      @Valid @RequestBody DiagnosisRejectRequest request) {
    return ApiResponse.success("Diagnosis rejected", diagnosisService.reject(reportId, request));
  }

  @PostMapping("/apply")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Record treatment applied + compute harvest lock")
  public ApiResponse<TreatmentApplicationResponse> apply(@PathVariable Long reportId,
      @RequestBody(required = false) DiagnosisApplyRequest request) {
    DiagnosisApplyRequest safeRequest = request != null ? request : new DiagnosisApplyRequest(null);
    return ApiResponse.success("Treatment application recorded",
        diagnosisService.apply(reportId, safeRequest));
  }
}
