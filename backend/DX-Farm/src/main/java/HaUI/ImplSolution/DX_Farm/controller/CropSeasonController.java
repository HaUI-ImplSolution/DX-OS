package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.CropSeasonCreateRequest;
import HaUI.ImplSolution.DX_Farm.dto.CropSeasonResponse;
import HaUI.ImplSolution.DX_Farm.dto.IsolationStatusResponse;
import HaUI.ImplSolution.DX_Farm.service.CropSeasonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "CropSeason", description = "Vụ mùa - Operational Data")
public class CropSeasonController {

  private final CropSeasonService cropSeasonService;

  public CropSeasonController(CropSeasonService cropSeasonService) {
    this.cropSeasonService = cropSeasonService;
  }

  @PostMapping("/parcels/{code}/crop-seasons")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Start a new crop season for a parcel",
      description = "FARMER: 404 if the parcel belongs to another farmer.")
  public ApiResponse<CropSeasonResponse> create(@PathVariable String code,
      @Valid @RequestBody CropSeasonCreateRequest request) {
    return ApiResponse.success("Crop season created", cropSeasonService.create(code, request));
  }

  @GetMapping("/parcels/{code}/crop-seasons")
  @Operation(summary = "List crop seasons for a parcel",
      description = "FARMER: 404 if the parcel belongs to another farmer.")
  public ApiResponse<List<CropSeasonResponse>> listByParcel(@PathVariable String code) {
    return ApiResponse.success(cropSeasonService.listByParcelCode(code));
  }

  @GetMapping("/crop-seasons/{id}")
  @Operation(summary = "Get crop season detail",
      description = "FARMER: 404 if the season's parcel belongs to another farmer.")
  public ApiResponse<CropSeasonResponse> getById(@PathVariable Long id) {
    return ApiResponse.success(cropSeasonService.getById(id));
  }

  @GetMapping("/crop-seasons/{id}/isolation-status")
  @Operation(summary = "Check whether a crop season is still under pesticide isolation (PHI)",
      description = "FARMER: 404 if the season's parcel belongs to another farmer.")
  public ApiResponse<IsolationStatusResponse> getIsolationStatus(@PathVariable Long id) {
    return ApiResponse.success(cropSeasonService.getIsolationStatus(id));
  }

  @PostMapping("/crop-seasons/{id}/harvest")
  @Operation(
      summary = "Declare harvest (poka-yoke: 422 POKA_YOKE_VIETGAP_VIOLATION if still isolated)")
  public ApiResponse<CropSeasonResponse> harvest(@PathVariable Long id) {
    return ApiResponse.success("Harvest recorded", cropSeasonService.harvest(id));
  }
}
