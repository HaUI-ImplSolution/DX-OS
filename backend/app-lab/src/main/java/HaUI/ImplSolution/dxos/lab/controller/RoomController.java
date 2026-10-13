// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.controller;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.lab.dto.BookingDtos.ScheduleResponse;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.RoomRequest;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.RoomResponse;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.RoomStatusChangeResponse;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.RoomStatusRequest;
import HaUI.ImplSolution.dxos.lab.model.RoomStatus;
import HaUI.ImplSolution.dxos.lab.model.RoomType;
import HaUI.ImplSolution.dxos.lab.service.RoomBookingService;
import HaUI.ImplSolution.dxos.lab.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
@Tag(name = "Room", description = "Rooms and their booking schedule")
public class RoomController {

  private final RoomService roomService;
  private final RoomBookingService bookingService;

  public RoomController(RoomService roomService, RoomBookingService bookingService) {
    this.roomService = roomService;
    this.bookingService = bookingService;
  }

  @GetMapping
  @Operation(summary = "List rooms by code",
      description = "Any signed-in user. Optional filters: type and status.")
  public ApiResponse<List<RoomResponse>> list(@RequestParam(required = false) RoomType type,
      @RequestParam(required = false) RoomStatus status) {
    return ApiResponse.success(roomService.list(type, status));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a room", description = "Any signed-in user. 404 for an unknown id.")
  public ApiResponse<RoomResponse> get(@PathVariable Long id) {
    return ApiResponse.success(roomService.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add a room",
      description = "TECHNICIAN or ADMIN only. 409 when the code" + " is already used.")
  public ApiResponse<RoomResponse> create(@Valid @RequestBody RoomRequest request) {
    return ApiResponse.success("Room created", roomService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @Operation(summary = "Change the details of a room",
      description = "TECHNICIAN or ADMIN only. 409 when the new code is already used.")
  public ApiResponse<RoomResponse> update(@PathVariable Long id,
      @Valid @RequestBody RoomRequest request) {
    return ApiResponse.success("Room updated", roomService.update(id, request));
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @Operation(summary = "Open a room or put it under maintenance",
      description = "TECHNICIAN or ADMIN only. A room under maintenance accepts no new bookings."
          + " The response lists the live bookings that fall in a room put under maintenance;"
          + " they stay as they are for the manager to handle.")
  public ApiResponse<RoomStatusChangeResponse> changeStatus(@PathVariable Long id,
      @Valid @RequestBody RoomStatusRequest request) {
    return ApiResponse.success("Room status changed",
        roomService.changeStatus(id, request.status()));
  }

  @GetMapping("/{id}/schedule")
  @Operation(summary = "Live bookings of a room in a range of days",
      description = "Any signed-in user. from defaults to today and to to six days after from"
          + " (yyyy-MM-dd, inclusive, at most 62 days). The requester name is shown to staff and"
          + " to the requester only.")
  public ApiResponse<ScheduleResponse> schedule(@PathVariable Long id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ApiResponse.success(bookingService.schedule(id, from, to));
  }
}
