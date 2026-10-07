package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.DiseaseReportResponse;
import HaUI.ImplSolution.DX_Farm.dto.ImagePrecheckResponse;
import HaUI.ImplSolution.DX_Farm.dto.RetakeRequest;
import HaUI.ImplSolution.DX_Farm.service.DiseaseReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "DiseaseReport", description = "Disease sighting reports")
public class DiseaseReportController {

  private final DiseaseReportService diseaseReportService;

  public DiseaseReportController(DiseaseReportService diseaseReportService) {
    this.diseaseReportService = diseaseReportService;
  }

  @PostMapping(value = "/crop-seasons/{cropSeasonId}/disease-reports",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('FARMER')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Report a disease sighting with a photo (reportedBy = logged-in user)",
      description = "FARMER only (ENGINEER and ADMIN get 403)."
          + " 404 if the season's parcel belongs to another farmer."
          + " With an Idempotency-Key, a repeated request returns the existing report without"
          + " storing the image again; 409 if the key was used for another crop season or user."
          + " retakeOf links a replacement photo to a NEEDS_RETAKE report in the same season. Quality is rechecked server-side; poor images get 422."
          + " 422 CROP_SEASON_HARVESTED if the crop season is already harvested.")
  public ApiResponse<DiseaseReportResponse> create(@PathVariable Long cropSeasonId,
      @RequestPart("image") MultipartFile image,
      @Parameter(description = "Client-generated UUID that makes retries of this request safe")
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestParam(required = false) Long retakeOf) {
    UUID clientRequestId = idempotencyKey == null ? null : UUID.fromString(idempotencyKey);
    return ApiResponse.success("Disease report created",
        diseaseReportService.create(cropSeasonId, image, clientRequestId, retakeOf));
  }

  @PostMapping(value = "/disease-reports/precheck", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('FARMER')")
  @Operation(summary = "Check image quality without storing a report",
      description = "Checks dimensions, brightness and sharpness only. Does not verify rice content. AI failures return 503.")
  public ApiResponse<ImagePrecheckResponse> precheck(@RequestPart("image") MultipartFile image) {
    return ApiResponse.success(diseaseReportService.precheck(image));
  }

  @PatchMapping("/disease-reports/{id}/request-retake")
  @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
  @Operation(summary = "Request a new photo",
      description = "Changes report status to NEEDS_RETAKE, cancels a diagnosis still waiting for approval,"
          + " and notifies the household. Leaves diagnosis decisions unchanged."
          + " 409 if the current diagnosis is already APPROVED or a retake was already requested.")
  public ApiResponse<DiseaseReportResponse> requestRetake(@PathVariable Long id,
      @Valid @RequestBody RetakeRequest request) {
    return ApiResponse.success(diseaseReportService.requestRetake(id, request.reason()));
  }

  @GetMapping("/crop-seasons/{cropSeasonId}/disease-reports")
  @Operation(summary = "List disease reports for a crop season",
      description = "FARMER: 404 if the season's parcel belongs to another farmer;"
          + " diagnosis is trimmed until APPROVED (see DiagnosisResponse)."
          + " includeDeleted=true (ADMIN only, otherwise 403) also returns soft-deleted reports.")
  public ApiResponse<List<DiseaseReportResponse>> listByCropSeason(@PathVariable Long cropSeasonId,
      @RequestParam(defaultValue = "false") boolean includeDeleted) {
    return ApiResponse.success(diseaseReportService.listByCropSeason(cropSeasonId, includeDeleted));
  }

  @GetMapping("/disease-reports/{id}")
  @Operation(summary = "Get a disease report (with nested diagnosis if present)",
      description = "FARMER: 404 if the season's parcel belongs to another farmer;"
          + " diagnosis is trimmed until APPROVED (see DiagnosisResponse).")
  public ApiResponse<DiseaseReportResponse> getById(@PathVariable Long id) {
    return ApiResponse.success(diseaseReportService.getById(id));
  }

  @DeleteMapping("/disease-reports/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Soft-delete a disease report entered by mistake",
      description = "ADMIN only.")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    diseaseReportService.delete(id);
    return ApiResponse.success("Disease report deleted", null);
  }
}
