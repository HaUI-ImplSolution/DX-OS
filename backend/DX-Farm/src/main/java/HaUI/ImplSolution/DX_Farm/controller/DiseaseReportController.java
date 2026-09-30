package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.DiseaseReportResponse;
import HaUI.ImplSolution.DX_Farm.service.DiseaseReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Report a disease sighting with a photo (reportedBy = logged-in user)",
      description = "FARMER: 404 if the season's parcel belongs to another farmer.")
  public ApiResponse<DiseaseReportResponse> create(@PathVariable Long cropSeasonId,
      @RequestPart("image") MultipartFile image) {
    return ApiResponse.success("Disease report created",
        diseaseReportService.create(cropSeasonId, image));
  }

  @GetMapping("/crop-seasons/{cropSeasonId}/disease-reports")
  @Operation(summary = "List disease reports for a crop season",
      description = "FARMER: 404 if the season's parcel belongs to another farmer;"
          + " diagnosis is trimmed until APPROVED (see DiagnosisResponse).")
  public ApiResponse<List<DiseaseReportResponse>> listByCropSeason(
      @PathVariable Long cropSeasonId) {
    return ApiResponse.success(diseaseReportService.listByCropSeason(cropSeasonId));
  }

  @GetMapping("/disease-reports/{id}")
  @Operation(summary = "Get a disease report (with nested diagnosis if present)",
      description = "FARMER: 404 if the season's parcel belongs to another farmer;"
          + " diagnosis is trimmed until APPROVED (see DiagnosisResponse).")
  public ApiResponse<DiseaseReportResponse> getById(@PathVariable Long id) {
    return ApiResponse.success(diseaseReportService.getById(id));
  }
}
