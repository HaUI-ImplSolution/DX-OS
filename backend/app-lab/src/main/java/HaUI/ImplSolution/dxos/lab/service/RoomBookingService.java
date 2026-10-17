// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ActionNotAllowedException;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseView;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowOperations;
import HaUI.ImplSolution.dxos.lab.LabSecurityPolicies;
import HaUI.ImplSolution.dxos.lab.dto.BookingDtos.BookingCreateRequest;
import HaUI.ImplSolution.dxos.lab.dto.BookingDtos.BookingRejectRequest;
import HaUI.ImplSolution.dxos.lab.dto.BookingDtos.BookingResponse;
import HaUI.ImplSolution.dxos.lab.dto.BookingDtos.ScheduleEntry;
import HaUI.ImplSolution.dxos.lab.dto.BookingDtos.ScheduleResponse;
import HaUI.ImplSolution.dxos.lab.model.BookingStatus;
import HaUI.ImplSolution.dxos.lab.model.Room;
import HaUI.ImplSolution.dxos.lab.model.RoomBooking;
import HaUI.ImplSolution.dxos.lab.model.RoomStatus;
import HaUI.ImplSolution.dxos.lab.repository.RoomBookingRepository;
import HaUI.ImplSolution.dxos.lab.repository.RoomRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Room bookings. A booking holds its slot from the request on, and the database refuses two live
// bookings that overlap, so two requests at the same moment cannot both succeed.
@Service
@Transactional(readOnly = true)
public class RoomBookingService {

  public static final int STEP_MINUTES = 30;
  public static final int MAX_HOURS = 4;
  public static final int MAX_ADVANCE_DAYS = 30;

  private static final int MAX_PAGE_SIZE = 100;

  private final RoomBookingRepository bookingRepository;
  private final RoomRepository roomRepository;
  private final WorkflowOperations workflow;
  private final LabUser user;
  private final Clock clock;

  public RoomBookingService(RoomBookingRepository bookingRepository, RoomRepository roomRepository,
      WorkflowOperations workflow, LabUser user, Clock clock) {
    this.bookingRepository = bookingRepository;
    this.roomRepository = roomRepository;
    this.workflow = workflow;
    this.user = user;
    this.clock = clock;
  }

  @Transactional
  public BookingResponse create(BookingCreateRequest request, UUID clientRequestId) {
    // Technicians decide on bookings and do not place them.
    if (LabSecurityPolicies.TECHNICIAN.equals(user.highestRole())) {
      throw new ActionNotAllowedException("Technicians do not book rooms");
    }
    if (clientRequestId != null) {
      var existing = bookingRepository.findByClientRequestId(clientRequestId);
      if (existing.isPresent()) {
        return BookingResponse.of(existing.get());
      }
    }
    Room room = roomRepository.findById(request.roomId())
        .orElseThrow(() -> new IllegalArgumentException("Unknown room: " + request.roomId()));
    validateSlot(room, request);
    if (bookingRepository.existsByRoom_IdAndActiveTrueAndStartsAtLessThanAndEndsAtGreaterThan(
        room.getId(), request.endsAt(), request.startsAt())) {
      throw slotTaken(room);
    }
    RoomBooking booking;
    try {
      booking = bookingRepository.saveAndFlush(RoomBooking.builder().room(room)
          .requesterId(user.id()).requesterName(user.name()).requesterRole(user.highestRole())
          .workspaceId(user.workspaceId()).title(request.title().strip())
          .attendees(request.attendees()).startsAt(request.startsAt()).endsAt(request.endsAt())
          .status(BookingStatus.PENDING).clientRequestId(clientRequestId).build());
    } catch (DataIntegrityViolationException e) {
      // The exclusion constraint caught a concurrent request for the same slot.
      throw slotTaken(room);
    }
    CaseView workflowCase = workflow.createCase(LabCaseTypes.ROOM_BOOKING,
        LabCaseTypes.ROOM_BOOKING_ENTITY, booking.getId(), user.actor());
    booking.setWorkflowCaseId(workflowCase.id());
    return BookingResponse.of(bookingRepository.save(booking));
  }

  // Own bookings for lecturers; technicians and administrators see all of them.
  public PageResponse<BookingResponse> list(String status, Long roomId, int page, int size) {
    Specification<RoomBooking> spec = Specification.unrestricted();
    if (!user.isStaff()) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("requesterId"), user.id()));
    }
    if (status != null) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
    }
    if (roomId != null) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("room").get("id"), roomId));
    }
    PageRequest pageRequest =
        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by(Sort.Order.desc("startsAt"), Sort.Order.desc("id")));
    return PageResponse.of(bookingRepository.findAll(spec, pageRequest), BookingResponse::of);
  }

  public BookingResponse get(Long id) {
    return BookingResponse.of(findVisible(id));
  }

  // Every user may look at a room's schedule; who booked a slot is shown to staff and to the
  // requester only.
  public ScheduleResponse schedule(Long roomId, LocalDate from, LocalDate to) {
    Room room = roomRepository.findById(roomId)
        .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
    LocalDate start = from != null ? from : LocalDate.now(clock);
    LocalDate end = to != null ? to : start.plusDays(6);
    if (end.isBefore(start)
        || Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() > 62) {
      throw new IllegalArgumentException("The schedule range must be between 0 and 62 days");
    }
    Instant rangeStart = start.atStartOfDay(BusinessTime.ZONE).toInstant();
    Instant rangeEnd = end.plusDays(1).atStartOfDay(BusinessTime.ZONE).toInstant();
    List<ScheduleEntry> entries = bookingRepository
        .findByRoom_IdAndActiveTrueAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAsc(roomId,
            rangeEnd, rangeStart)
        .stream()
        .map(booking -> new ScheduleEntry(booking.getId(), booking.getTitle(),
            booking.getStartsAt(), booking.getEndsAt(), booking.getStatus(), booking.getAttendees(),
            user.isStaff() || booking.getRequesterId().equals(user.id())
                ? booking.getRequesterName()
                : null))
        .toList();
    return new ScheduleResponse(room.getId(), room.getCode(), rangeStart, rangeEnd, entries);
  }

  @Transactional
  public BookingResponse approve(Long id) {
    RoomBooking booking = findVisible(id);
    requireStatus(booking, BookingStatus.PENDING);
    requireNotOwn(booking);
    if (!booking.getStartsAt().isAfter(clock.instant())) {
      throw new PokaYokeViolationException("BOOKING_IN_THE_PAST",
          "Khung giờ đặt phòng đã bắt đầu, không thể duyệt");
    }
    if (booking.getRoom().getStatus() != RoomStatus.AVAILABLE) {
      throw new PokaYokeViolationException("ROOM_UNDER_MAINTENANCE",
          "Phòng %s đang bảo trì".formatted(booking.getRoom().getCode()));
    }
    workflow.decide(booking.getWorkflowCaseId(), BookingStatus.APPROVED, user.actor(), null);
    return BookingResponse.of(booking);
  }

  @Transactional
  public BookingResponse reject(Long id, BookingRejectRequest request) {
    RoomBooking booking = findVisible(id);
    requireStatus(booking, BookingStatus.PENDING);
    requireNotOwn(booking);
    String reason = request == null ? null : request.reason();
    if (reason == null || reason.isBlank()) {
      throw new PokaYokeViolationException("REJECTION_REASON_REQUIRED",
          "Cần ghi lý do từ chối lịch đặt phòng");
    }
    workflow.decide(booking.getWorkflowCaseId(), BookingStatus.REJECTED, user.actor(),
        reason.strip());
    return BookingResponse.of(booking);
  }

  @Transactional
  public BookingResponse cancel(Long id) {
    RoomBooking booking = findVisible(id);
    if (!List.of(BookingStatus.PENDING, BookingStatus.APPROVED).contains(booking.getStatus())) {
      throw new InvalidStateException(
          "Booking %d is %s and cannot be cancelled".formatted(id, booking.getStatus()));
    }
    if (!user.isStaff() && !booking.getRequesterId().equals(user.id())) {
      throw new ActionNotAllowedException("Only the requester can cancel the booking");
    }
    workflow.decide(booking.getWorkflowCaseId(), BookingStatus.CANCELLED, user.actor(), null);
    return BookingResponse.of(booking);
  }

  private void validateSlot(Room room, BookingCreateRequest request) {
    if (room.getStatus() != RoomStatus.AVAILABLE) {
      throw new PokaYokeViolationException("ROOM_UNDER_MAINTENANCE",
          "Phòng %s đang bảo trì nên chưa nhận đặt".formatted(room.getCode()));
    }
    Instant start = request.startsAt();
    Instant end = request.endsAt();
    if (!end.isAfter(start)) {
      throw new PokaYokeViolationException("BOOKING_RANGE_INVALID",
          "Giờ kết thúc phải sau giờ bắt đầu");
    }
    if (!aligned(start) || !aligned(end)) {
      throw new PokaYokeViolationException("BOOKING_STEP_INVALID",
          "Giờ đặt phòng phải theo bước %d phút".formatted(STEP_MINUTES));
    }
    if (Duration.between(start, end).compareTo(Duration.ofHours(MAX_HOURS)) > 0) {
      throw new PokaYokeViolationException("BOOKING_TOO_LONG",
          "Mỗi lần đặt tối đa %d giờ".formatted(MAX_HOURS));
    }
    Instant now = clock.instant();
    if (!start.isAfter(now)) {
      throw new PokaYokeViolationException("BOOKING_IN_THE_PAST",
          "Không thể đặt phòng vào thời điểm đã qua");
    }
    if (start.isAfter(now.plus(Duration.ofDays(MAX_ADVANCE_DAYS)))) {
      throw new PokaYokeViolationException("BOOKING_TOO_FAR",
          "Chỉ đặt phòng trước tối đa %d ngày".formatted(MAX_ADVANCE_DAYS));
    }
    if (request.attendees() > room.getCapacity()) {
      throw new PokaYokeViolationException("ROOM_CAPACITY_EXCEEDED",
          "Phòng %s chỉ chứa tối đa %d người".formatted(room.getCode(), room.getCapacity()));
    }
  }

  private static boolean aligned(Instant instant) {
    ZonedDateTime time = instant.atZone(BusinessTime.ZONE);
    return time.getSecond() == 0 && time.getNano() == 0 && time.getMinute() % STEP_MINUTES == 0;
  }

  private static PokaYokeViolationException slotTaken(Room room) {
    return new PokaYokeViolationException("ROOM_SLOT_TAKEN",
        "Phòng %s đã có lịch trong khung giờ này".formatted(room.getCode()));
  }

  private RoomBooking findVisible(Long id) {
    RoomBooking booking = bookingRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Room booking not found: " + id));
    if (!user.isStaff() && !booking.getRequesterId().equals(user.id())) {
      throw new ResourceNotFoundException("Room booking not found: " + id);
    }
    return booking;
  }

  // Nobody decides on their own booking.
  private void requireNotOwn(RoomBooking booking) {
    if (booking.getRequesterId().equals(user.id())) {
      throw new ActionNotAllowedException("You cannot decide your own booking");
    }
  }

  private static void requireStatus(RoomBooking booking, String expected) {
    if (!expected.equals(booking.getStatus())) {
      throw new InvalidStateException("Booking %d is %s, expected %s".formatted(booking.getId(),
          booking.getStatus(), expected));
    }
  }
}
