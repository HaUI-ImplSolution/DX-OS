package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.CropSeasonCreateRequest;
import HaUI.ImplSolution.DX_Farm.dto.CropSeasonResponse;
import HaUI.ImplSolution.DX_Farm.dto.CropSeasonSuggestionResponse;
import HaUI.ImplSolution.DX_Farm.dto.IsolationStatusResponse;
import HaUI.ImplSolution.DX_Farm.service.CropSeasonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  @PreAuthorize("hasAnyRole('FARMER','ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Start a new crop season for a parcel",
      description = "FARMER or ADMIN only (ENGINEER gets 403)."
          + " FARMER: 404 if the parcel belongs to another farmer; the season is PENDING until an"
          + " administrator approves it. ADMIN: approved at once."
          + " The code is generated (<parcel code>-<year><DX|HT|TD>). riceVarietyCode must be in"
          + " the catalogue (400). seasonType defaults to the one suggested by the start month (400"
          + " when the month matches none). expectedHarvestDate defaults to the suggested date;"
          + " outside the growth range of the variety it needs reasonForOverride (400)."
          + " 422 PARCEL_NOT_APPROVED while the parcel is not approved; 422 TOO_MANY_PENDING_REQUESTS"
          + " when the farmer already has the maximum of requests waiting."
          + " 409 if the parcel already has an active crop season, or one of the same type in the"
          + " same year. With an Idempotency-Key, a repeated request returns the existing season.")
  public ApiResponse<CropSeasonResponse> create(@PathVariable String code,
      @Valid @RequestBody CropSeasonCreateRequest request,
      @Parameter(description = "Client-generated UUID that makes retries of this request safe")
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    UUID clientRequestId = idempotencyKey == null ? null : UUID.fromString(idempotencyKey);
    return ApiResponse.success("Crop season created",
        cropSeasonService.create(code, request, clientRequestId));
  }

  @PostMapping("/crop-seasons/{id}/resubmit")
  @PreAuthorize("hasAnyRole('FARMER','ADMIN')")
  @Operation(summary = "Send a rejected crop season again with corrected values",
      description = "FARMER or ADMIN only. FARMER: 404 if the season's parcel belongs to another"
          + " farmer. 409 unless the season is REJECTED. Same rules as creation.")
  public ApiResponse<CropSeasonResponse> resubmit(@PathVariable Long id,
      @Valid @RequestBody CropSeasonCreateRequest request) {
    return ApiResponse.success("Crop season sent again", cropSeasonService.resubmit(id, request));
  }

  @GetMapping("/crop-seasons/suggestion")
  @Operation(summary = "Suggested season type and harvest date for a variety and start date",
      description = "seasonType is null when the start month matches no season window."
          + " 400 for a variety that is not in the catalogue.")
  public ApiResponse<CropSeasonSuggestionResponse> suggest(@RequestParam String riceVarietyCode,
      @RequestParam LocalDate startDate) {
    return ApiResponse.success(cropSeasonService.suggest(riceVarietyCode, startDate));
  }

  @GetMapping("/parcels/{code}/crop-seasons")
  @Operation(summary = "List crop seasons for a parcel",
      description = "FARMER: 404 if the parcel belongs to another farmer."
          + " includeDeleted=true (ADMIN only, otherwise 403) also returns soft-deleted seasons.")
  public ApiResponse<List<CropSeasonResponse>> listByParcel(@PathVariable String code,
      @RequestParam(defaultValue = "false") boolean includeDeleted) {
    return ApiResponse.success(cropSeasonService.listByParcelCode(code, includeDeleted));
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
  @PreAuthorize("hasAnyRole('FARMER','ADMIN')")
  @Operation(
      summary = "Declare harvest (poka-yoke: 422 POKA_YOKE_VIETGAP_VIOLATION if still isolated)",
      description = "FARMER or ADMIN only (ENGINEER gets 403). FARMER: 404 if the season's parcel belongs to another farmer."
          + " 409 if the season was modified concurrently.")
  public ApiResponse<CropSeasonResponse> harvest(@PathVariable Long id) {
    return ApiResponse.success("Harvest recorded", cropSeasonService.harvest(id));
  }

  @DeleteMapping("/crop-seasons/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Soft-delete a crop season entered by mistake", description = "ADMIN only.")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    cropSeasonService.delete(id);
    return ApiResponse.success("Crop season deleted", null);
  }
}
