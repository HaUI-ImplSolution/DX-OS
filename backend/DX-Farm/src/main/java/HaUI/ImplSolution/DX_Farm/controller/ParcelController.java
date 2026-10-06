package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.ParcelCreateRequest;
import HaUI.ImplSolution.DX_Farm.dto.ParcelResponse;
import HaUI.ImplSolution.DX_Farm.service.ParcelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parcels")
@Tag(name = "Parcel", description = "Thửa ruộng - Operational Data")
public class ParcelController {

  private final ParcelService parcelService;

  public ParcelController(ParcelService parcelService) {
    this.parcelService = parcelService;
  }

  @GetMapping
  @Operation(summary = "List parcels",
      description = "FARMER: only parcels of the household linked to the account."
          + " Other roles: all parcels. includeDeleted=true (ADMIN only, otherwise 403) also"
          + " returns soft-deleted parcels.")
  public ApiResponse<List<ParcelResponse>> listAll(
      @RequestParam(defaultValue = "false") boolean includeDeleted) {
    return ApiResponse.success(parcelService.listAll(includeDeleted));
  }

  @GetMapping("/{code}")
  @Operation(summary = "Get a parcel by its business code (e.g. TD-01)",
      description = "FARMER: 404 if the parcel belongs to another farmer.")
  public ApiResponse<ParcelResponse> getByCode(@PathVariable String code) {
    return ApiResponse.success(parcelService.getByCode(code));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('FARMER','ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Register a new parcel",
      description = "FARMER or ADMIN only (ENGINEER gets 403). FARMER: householdCode is ignored and"
          + " the parcel belongs to the household linked to the account (403 if none is linked)."
          + " ADMIN: householdCode is required. 409 if the parcel code is already in use.")
  public ApiResponse<ParcelResponse> create(@Valid @RequestBody ParcelCreateRequest request) {
    return ApiResponse.success("Parcel created", parcelService.create(request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Soft-delete a parcel entered by mistake",
      description = "ADMIN only. 409 while the parcel still has crop seasons.")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    parcelService.delete(id);
    return ApiResponse.success("Parcel deleted", null);
  }
}
