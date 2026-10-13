// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseView;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowDecisionHandler;
import HaUI.ImplSolution.dxos.lab.model.BookingStatus;
import HaUI.ImplSolution.dxos.lab.model.RoomBooking;
import HaUI.ImplSolution.dxos.lab.repository.RoomBookingRepository;
import org.springframework.stereotype.Component;

// Mirrors every step of a ROOM_BOOKING case onto the booking. A rejected or cancelled booking
// releases its slot in the same transaction.
@Component
public class RoomBookingDecisionHandler implements WorkflowDecisionHandler {

  private final RoomBookingRepository bookingRepository;
  private final LabNotifier notifier;

  public RoomBookingDecisionHandler(RoomBookingRepository bookingRepository, LabNotifier notifier) {
    this.bookingRepository = bookingRepository;
    this.notifier = notifier;
  }

  @Override
  public String caseType() {
    return LabCaseTypes.ROOM_BOOKING;
  }

  @Override
  public void onDecision(CaseView workflowCase, String target, String reason) {
    RoomBooking booking = bookingRepository.findById(workflowCase.entityId()).orElseThrow(
        () -> new ResourceNotFoundException(
            "Room booking not found: " + workflowCase.entityId()));
    booking.setStatus(target);
    String room = booking.getRoom().getCode();
    switch (target) {
      case BookingStatus.APPROVED -> notifier.tell(booking.getRequesterId(),
          LabNotificationTypes.BOOKING_APPROVED, "Lịch đặt phòng được duyệt",
          "Lịch đặt phòng %s cho \"%s\" đã được duyệt.".formatted(room, booking.getTitle()),
          LabCaseTypes.ROOM_BOOKING_ENTITY, booking.getId());
      case BookingStatus.REJECTED -> {
        booking.setActive(false);
        booking.setRejectionReason(reason);
        notifier.tell(booking.getRequesterId(), LabNotificationTypes.BOOKING_REJECTED,
            "Lịch đặt phòng bị từ chối", "Lịch đặt phòng %s cho \"%s\" bị từ chối. Lý do: %s"
                .formatted(room, booking.getTitle(), reason),
            LabCaseTypes.ROOM_BOOKING_ENTITY, booking.getId());
      }
      case BookingStatus.CANCELLED -> booking.setActive(false);
      default -> {
      }
    }
    bookingRepository.save(booking);
  }
}
