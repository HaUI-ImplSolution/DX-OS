// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.dto;

import HaUI.ImplSolution.dxos.lab.model.RoomBooking;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

// Requests and responses of room bookings.
public final class BookingDtos {

  public record BookingCreateRequest(@NotNull Long roomId, @NotBlank @Size(max = 255) String title,
      @NotNull @Min(1) Integer attendees, @NotNull Instant startsAt, @NotNull Instant endsAt) {}

  public record BookingRejectRequest(@Size(max = 1000) String reason) {}

  public record BookingResponse(Long id, Long roomId, String roomCode, String requesterId,
      String requesterName, String requesterRole, String title, int attendees, Instant startsAt,
      Instant endsAt, String status, String rejectionReason, Instant createdAt) {

    public static BookingResponse of(RoomBooking booking) {
      return new BookingResponse(booking.getId(), booking.getRoom().getId(),
          booking.getRoom().getCode(), booking.getRequesterId(), booking.getRequesterName(),
          booking.getRequesterRole(), booking.getTitle(), booking.getAttendees(),
          booking.getStartsAt(), booking.getEndsAt(), booking.getStatus(),
          booking.getRejectionReason(), booking.getCreatedAt());
    }
  }

  // One slot of a room schedule; requesterName is shown to staff and the requester only.
  public record ScheduleEntry(Long bookingId, String title, Instant startsAt, Instant endsAt,
      String status, int attendees, String requesterName) {}

  public record ScheduleResponse(Long roomId, String roomCode, Instant from, Instant to,
      List<ScheduleEntry> entries) {}

  private BookingDtos() {}
}
