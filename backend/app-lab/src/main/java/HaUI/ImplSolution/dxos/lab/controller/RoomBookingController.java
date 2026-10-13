// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.controller;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.lab.dto.BookingDtos.BookingCreateRequest;
import HaUI.ImplSolution.dxos.lab.dto.BookingDtos.BookingRejectRequest;
import HaUI.ImplSolution.dxos.lab.dto.BookingDtos.BookingResponse;
import HaUI.ImplSolution.dxos.lab.service.RoomBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/room-bookings")
@Tag(name = "RoomBooking", description = "Booking lab and practice rooms - Process")
public class RoomBookingController {

  private final RoomBookingService bookingService;

  public RoomBookingController(RoomBookingService bookingService) {
    this.bookingService = bookingService;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('LECTURER','TECHNICIAN','ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Book a room for a time range",
      description = "LECTURER, TECHNICIAN or ADMIN (a STUDENT gets 403). Times fall on 30-minute"
          + " marks, last at most 4 hours and start within the next 30 days. 422 with"
          + " BOOKING_STEP_INVALID, BOOKING_TOO_LONG, BOOKING_IN_THE_PAST, BOOKING_TOO_FAR,"
          + " ROOM_CAPACITY_EXCEEDED, ROOM_UNDER_MAINTENANCE, or ROOM_SLOT_TAKEN when another"
          + " live booking overlaps. With an Idempotency-Key, a repeated request returns the"
          + " existing booking.")
  public ApiResponse<BookingResponse> create(@Valid @RequestBody BookingCreateRequest request,
      @Parameter(description = "Client-generated UUID that makes retries of this request safe")
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    UUID clientRequestId = idempotencyKey == null ? null : UUID.fromString(idempotencyKey);
    return ApiResponse.success("Booking requested",
        bookingService.create(request, clientRequestId));
  }

  @GetMapping
  @Operation(summary = "List bookings, latest start first",
      description = "Own bookings; TECHNICIAN and ADMIN see all of them. Filters: status, roomId."
          + " size is capped at 100.")
  public ApiResponse<PageResponse<BookingResponse>> list(
      @RequestParam(required = false) String status, @RequestParam(required = false) Long roomId,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(bookingService.list(status, roomId, page, size));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a booking",
      description = "404 for the booking of someone else unless TECHNICIAN or ADMIN.")
  public ApiResponse<BookingResponse> get(@PathVariable Long id) {
    return ApiResponse.success(bookingService.get(id));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @Operation(summary = "Approve a booking",
      description = "TECHNICIAN or ADMIN only. 409 unless PENDING. 422 BOOKING_IN_THE_PAST once"
          + " the slot has started, ROOM_UNDER_MAINTENANCE while the room is closed.")
  public ApiResponse<BookingResponse> approve(@PathVariable Long id) {
    return ApiResponse.success("Booking approved", bookingService.approve(id));
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @Operation(summary = "Reject a booking and free its slot",
      description = "TECHNICIAN or ADMIN only. reason is required (422"
          + " REJECTION_REASON_REQUIRED). 409 unless PENDING.")
  public ApiResponse<BookingResponse> reject(@PathVariable Long id,
      @Valid @RequestBody(required = false) BookingRejectRequest request) {
    return ApiResponse.success("Booking rejected", bookingService.reject(id, request));
  }

  @PostMapping("/{id}/cancel")
  @Operation(summary = "Cancel a booking and free its slot",
      description = "The requester, TECHNICIAN or ADMIN. 409 unless PENDING or APPROVED.")
  public ApiResponse<BookingResponse> cancel(@PathVariable Long id) {
    return ApiResponse.success("Booking cancelled", bookingService.cancel(id));
  }
}
