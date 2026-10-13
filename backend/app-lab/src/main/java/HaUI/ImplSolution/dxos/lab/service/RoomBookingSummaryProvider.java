// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummary;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummaryProvider;
import HaUI.ImplSolution.dxos.lab.model.RoomBooking;
import HaUI.ImplSolution.dxos.lab.repository.RoomBookingRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Describes room bookings for the work queue.
@Component
@Transactional(readOnly = true)
public class RoomBookingSummaryProvider implements CaseSummaryProvider {

  private final RoomBookingRepository bookingRepository;

  public RoomBookingSummaryProvider(RoomBookingRepository bookingRepository) {
    this.bookingRepository = bookingRepository;
  }

  @Override
  public String entityType() {
    return LabCaseTypes.ROOM_BOOKING_ENTITY;
  }

  @Override
  public Map<Long, CaseSummary> summarize(Collection<Long> entityIds) {
    return bookingRepository.findByIdIn(entityIds).stream()
        .collect(Collectors.toMap(RoomBooking::getId, RoomBookingSummaryProvider::summary));
  }

  @Override
  public List<Long> entityIdsOfWorkspace(Long workspaceId) {
    return bookingRepository.findByWorkspaceId(workspaceId).stream().map(RoomBooking::getId)
        .toList();
  }

  private static CaseSummary summary(RoomBooking booking) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("room", booking.getRoom().getCode());
    details.put("title", booking.getTitle());
    details.put("attendees", booking.getAttendees());
    details.put("startsAt", booking.getStartsAt());
    details.put("endsAt", booking.getEndsAt());
    details.put("rejectionReason", booking.getRejectionReason());
    return new CaseSummary("%s %s".formatted(booking.getRoom().getCode(), booking.getTitle()),
        booking.getWorkspaceId(), booking.getRequesterName(), details);
  }
}
