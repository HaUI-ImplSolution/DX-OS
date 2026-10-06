package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisApplyRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisCreateRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisRejectRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisResponse;
import HaUI.ImplSolution.DX_Farm.dto.SymptomSuggestRequest;
import HaUI.ImplSolution.DX_Farm.dto.SymptomSuggestionResponse;
import HaUI.ImplSolution.DX_Farm.dto.TreatmentApplicationResponse;
import HaUI.ImplSolution.DX_Farm.service.DiagnosisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
  @Operation(summary = "Record an engineer diagnosis, with optional symptom text",
      description = "Preserves diagnosis history. Missing treatment rules produce review-only recommendations, never a fabricated dose or PHI.")
  public ApiResponse<DiagnosisResponse> create(@PathVariable Long reportId,
      @Valid @RequestBody DiagnosisCreateRequest request) {
    return ApiResponse.success("Diagnosis recorded", diagnosisService.create(reportId, request));
  }

  @PostMapping("/suggest")
  @Operation(summary = "Rank catalog diseases from symptom text",
      description = "RAG evidence only; scores are similarities, not diagnostic confidence. Does not save a diagnosis.")
  public ApiResponse<List<SymptomSuggestionResponse>> suggest(@PathVariable Long reportId,
      @Valid @RequestBody SymptomSuggestRequest request) {
    return ApiResponse.success(diagnosisService.suggest(reportId, request.symptomText()));
  }

  @GetMapping("/history")
  @Operation(summary = "List all diagnoses for this report, newest first",
      description = "Staff only. Earlier diagnoses and treatment locks are preserved.")
  public ApiResponse<List<DiagnosisResponse>> history(@PathVariable Long reportId) {
    return ApiResponse.success(diagnosisService.history(reportId));
  }

  @PostMapping("/ai")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Run or rerun AI diagnosis outside the database transaction",
      description = "Preserves earlier diagnoses. 409 if a newer diagnosis arrives during inference or the report needs retake. Latest saved id is current.")
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
