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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
      description = "FARMER: only parcels whose farmerName matches the user's name (case-insensitive)."
          + " Other roles: all parcels.")
  public ApiResponse<List<ParcelResponse>> listAll() {
    return ApiResponse.success(parcelService.listAll());
  }

  @GetMapping("/{code}")
  @Operation(summary = "Get a parcel by its business code (e.g. TD-01)",
      description = "FARMER: 404 if the parcel belongs to another farmer.")
  public ApiResponse<ParcelResponse> getByCode(@PathVariable String code) {
    return ApiResponse.success(parcelService.getByCode(code));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Register a new parcel")
  public ApiResponse<ParcelResponse> create(@Valid @RequestBody ParcelCreateRequest request) {
    return ApiResponse.success("Parcel created", parcelService.create(request));
  }
}
