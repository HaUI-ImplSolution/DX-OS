// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.controller;

import HaUI.ImplSolution.dxos.core.api.audit.AuditEvent;
import HaUI.ImplSolution.dxos.core.api.audit.AuditRecorder;
import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.request.IdempotencyKey;
import HaUI.ImplSolution.dxos.farm.dto.ParcelCreateRequest;
import HaUI.ImplSolution.dxos.farm.dto.ParcelResponse;
import HaUI.ImplSolution.dxos.farm.dto.ParcelUpdateRequest;
import HaUI.ImplSolution.dxos.farm.dto.WeatherResponse;
import HaUI.ImplSolution.dxos.farm.service.ParcelService;
import HaUI.ImplSolution.dxos.farm.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/parcels")
@Tag(name = "Parcel", description = "Thửa ruộng - Operational Data")
public class ParcelController {

  private final ParcelService parcelService;
  private final WeatherService weatherService;
  private final AuditRecorder auditRecorder;

  public ParcelController(ParcelService parcelService, WeatherService weatherService,
      AuditRecorder auditRecorder) {
    this.parcelService = parcelService;
    this.weatherService = weatherService;
    this.auditRecorder = auditRecorder;
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

  @GetMapping("/{code}/weather")
  @Operation(summary = "Current weather and 5-day forecast at the parcel position",
      description = "Data from Open-Meteo, cached for an hour. When Open-Meteo cannot be reached"
          + " the closest stored reference point is returned with isSnapshot=true and a note."
          + " FARMER: 404 if the parcel belongs to another farmer. 422 PARCEL_LOCATION_MISSING"
          + " when the parcel has no GPS position.")
  public ApiResponse<WeatherResponse> weather(@PathVariable String code) {
    return ApiResponse.success(weatherService.forParcel(code));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('FARMER','ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Register a new parcel",
      description = "FARMER or ADMIN only (ENGINEER gets 403). The code is generated"
          + " (<HTX>-<household>-T<number>), never sent. latitude and longitude are required (400)."
          + " FARMER: householdCode is ignored and the parcel belongs to the household linked to"
          + " the account (403 if none is linked); the parcel is PENDING until an administrator"
          + " approves it; gpsAccuracyMeters is required and must not exceed the configured limit,"
          + " and mockedLocation=true is refused (422 GPS_ACCURACY_TOO_LOW, GPS_MOCKED);"
          + " 422 TOO_MANY_PENDING_REQUESTS when the farmer already has the maximum of requests"
          + " waiting. ADMIN: householdCode is required and the parcel is approved at once."
          + " With an Idempotency-Key, a repeated request returns the existing parcel; 409 if the"
          + " key was used for another household.")
  public ApiResponse<ParcelResponse> create(@Valid @RequestBody ParcelCreateRequest request,
      @Parameter(description = "Client-generated UUID that makes retries of this request safe")
      @RequestHeader(value = IdempotencyKey.HEADER, required = false) String idempotencyKey) {
    UUID clientRequestId = IdempotencyKey.parse(idempotencyKey);
    return ApiResponse.success("Parcel created", parcelService.create(request, clientRequestId));
  }

  @PostMapping("/{id}/resubmit")
  @PreAuthorize("hasAnyRole('FARMER','ADMIN')")
  @Operation(summary = "Send a rejected parcel again with corrected values",
      description = "FARMER or ADMIN only. FARMER: 404 if the parcel belongs to another farmer."
          + " 409 unless the parcel is REJECTED. Same GPS and pending-request rules as creation.")
  public ApiResponse<ParcelResponse> resubmit(@PathVariable Long id,
      @Valid @RequestBody ParcelUpdateRequest request) {
    return ApiResponse.success("Parcel sent again", parcelService.resubmit(id, request));
  }

  @PostMapping("/{id}/edit-requests")
  @PreAuthorize("hasAnyRole('FARMER','ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Ask to change the area or location of an approved parcel",
      description = "FARMER or ADMIN only. The parcel keeps its values until an administrator"
          + " approves the request (an ADMIN's own request applies at once). FARMER: 404 if the"
          + " parcel belongs to another farmer. 409 unless the parcel is APPROVED or when a change"
          + " request is already waiting. Same GPS and pending-request rules as creation.")
  public ApiResponse<ParcelResponse> requestEdit(@PathVariable Long id,
      @Valid @RequestBody ParcelUpdateRequest request) {
    return ApiResponse.success("Change request created", parcelService.requestEdit(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Soft-delete a parcel entered by mistake",
      description = "ADMIN only. 409 while the parcel still has crop seasons.")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    parcelService.delete(id);
    auditRecorder.record(AuditEvent.success("PARCEL_DELETED", "PARCEL", String.valueOf(id)));
    return ApiResponse.success("Parcel deleted", null);
  }
}
