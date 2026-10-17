// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.controller;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.DeviceRequest;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.DeviceResponse;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.MaintenanceCompleteRequest;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.MaintenanceResponse;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.MaintenanceStartRequest;
import HaUI.ImplSolution.dxos.lab.model.DeviceStatus;
import HaUI.ImplSolution.dxos.lab.service.DeviceMaintenanceService;
import HaUI.ImplSolution.dxos.lab.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
@Tag(name = "Device", description = "Device inventory and maintenance log")
public class DeviceController {

  private final DeviceService deviceService;
  private final DeviceMaintenanceService maintenanceService;

  public DeviceController(DeviceService deviceService,
      DeviceMaintenanceService maintenanceService) {
    this.deviceService = deviceService;
    this.maintenanceService = maintenanceService;
  }

  @GetMapping
  @Operation(summary = "List devices by asset code",
      description = "Any signed-in user. Optional filters: roomId, status and deviceType (part of"
          + " the type, ignoring case).")
  public ApiResponse<List<DeviceResponse>> list(@RequestParam(required = false) Long roomId,
      @RequestParam(required = false) DeviceStatus status,
      @RequestParam(required = false) String deviceType) {
    return ApiResponse.success(deviceService.list(roomId, status, deviceType));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a device", description = "Any signed-in user. 404 for an unknown id.")
  public ApiResponse<DeviceResponse> get(@PathVariable Long id) {
    return ApiResponse.success(deviceService.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Register a device",
      description = "TECHNICIAN or ADMIN only. The device starts AVAILABLE. 422 ASSET_CODE_TAKEN when the asset"
          + " code is already used; 400 for an unknown room.")
  public ApiResponse<DeviceResponse> create(@Valid @RequestBody DeviceRequest request) {
    return ApiResponse.success("Device created", deviceService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @Operation(summary = "Change the details of a device",
      description = "TECHNICIAN or ADMIN only. The status is not changed here: loans and"
          + " maintenance move it. 422 ASSET_CODE_TAKEN when the new asset code is already used.")
  public ApiResponse<DeviceResponse> update(@PathVariable Long id,
      @Valid @RequestBody DeviceRequest request) {
    return ApiResponse.success("Device updated", deviceService.update(id, request));
  }

  @PostMapping("/{id}/retire")
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @Operation(summary = "Write a device off",
      description = "TECHNICIAN or ADMIN only. 422 DEVICE_ON_LOAN while the device is out on loan.")
  public ApiResponse<DeviceResponse> retire(@PathVariable Long id) {
    return ApiResponse.success("Device retired", deviceService.retire(id));
  }

  @GetMapping("/{id}/maintenance")
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @Operation(summary = "Maintenance log of a device, newest first",
      description = "TECHNICIAN or ADMIN only.")
  public ApiResponse<List<MaintenanceResponse>> maintenance(@PathVariable Long id) {
    return ApiResponse.success(maintenanceService.list(id));
  }

  @PostMapping("/{id}/maintenance")
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Start a repair, inspection or replacement",
      description = "TECHNICIAN or ADMIN only. A repair or replacement sets the device to"
          + " UNDER_REPAIR until it is completed. 422 DEVICE_ON_LOAN, DEVICE_RETIRED or"
          + " MAINTENANCE_IN_PROGRESS.")
  public ApiResponse<MaintenanceResponse> startMaintenance(@PathVariable Long id,
      @Valid @RequestBody MaintenanceStartRequest request) {
    return ApiResponse.success("Maintenance started", maintenanceService.start(id, request));
  }

  @PostMapping("/{id}/maintenance/{maintenanceId}/complete")
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @Operation(summary = "Complete a maintenance entry",
      description = "TECHNICIAN or ADMIN only. A device under repair becomes AVAILABLE again."
          + " 409 when the entry is already completed.")
  public ApiResponse<MaintenanceResponse> completeMaintenance(@PathVariable Long id,
      @PathVariable Long maintenanceId,
      @Valid @RequestBody(required = false) MaintenanceCompleteRequest request) {
    return ApiResponse.success("Maintenance completed",
        maintenanceService.complete(id, maintenanceId, request));
  }
}
