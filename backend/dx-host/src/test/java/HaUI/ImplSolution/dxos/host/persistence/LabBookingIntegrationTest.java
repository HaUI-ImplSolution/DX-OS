// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.exception.ActionNotAllowedException;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.config.TimeConfig;
import HaUI.ImplSolution.dxos.core.notification.NotificationInboxService;
import HaUI.ImplSolution.dxos.core.notification.NotificationTypes;
import HaUI.ImplSolution.dxos.core.workflow.WorkflowDefinitions;
import HaUI.ImplSolution.dxos.core.workflow.WorkflowService;
import HaUI.ImplSolution.dxos.lab.LabNotificationConfig;
import HaUI.ImplSolution.dxos.lab.LabWorkflowConfig;
import HaUI.ImplSolution.dxos.lab.dto.BookingDtos.BookingCreateRequest;
import HaUI.ImplSolution.dxos.lab.dto.BookingDtos.BookingRejectRequest;
import HaUI.ImplSolution.dxos.lab.dto.BookingDtos.BookingResponse;
import HaUI.ImplSolution.dxos.lab.model.Room;
import HaUI.ImplSolution.dxos.lab.model.RoomStatus;
import HaUI.ImplSolution.dxos.lab.model.RoomType;
import HaUI.ImplSolution.dxos.lab.repository.RoomRepository;
import HaUI.ImplSolution.dxos.lab.service.LabNotifier;
import HaUI.ImplSolution.dxos.lab.service.LabUser;
import HaUI.ImplSolution.dxos.lab.service.RoomBookingDecisionHandler;
import HaUI.ImplSolution.dxos.lab.service.RoomBookingService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Room booking rules: the limits on length, step, lead time, capacity and room state, who decides,
// who sees what, and that a booking which ends early gives its slot back.
@DataJpaTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({RoomBookingService.class, RoomBookingDecisionHandler.class, LabUser.class,
    LabNotifier.class, NotificationInboxService.class, NotificationTypes.class,
    LabNotificationConfig.class, WorkflowService.class, WorkflowDefinitions.class,
    LabWorkflowConfig.class, TimeConfig.class, PersistenceTestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LabBookingIntegrationTest {

  private static final int CAPACITY = 30;

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    MigratedDatabase.migrate(POSTGRES);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired
  RoomBookingService bookings;
  @Autowired
  RoomRepository rooms;
  @Autowired
  NotificationInboxService inbox;
  @Autowired
  Clock clock;
  @MockitoBean
  UserContext userContext;

  LocalDate tomorrow;
  String lecturer;
  String otherLecturer;
  String technician;
  Room room;

  @BeforeEach
  void setup() {
    tomorrow = LocalDate.now(clock).plusDays(1);
    lecturer = "lecturer-" + UUID.randomUUID();
    otherLecturer = "lecturer-" + UUID.randomUUID();
    technician = "tech-" + UUID.randomUUID();
    room = room(RoomStatus.AVAILABLE);
  }

  private void actAs(String userId, String role) {
    reset(userContext);
    when(userContext.currentUserId()).thenReturn(userId);
    when(userContext.currentFullName()).thenReturn("User " + userId);
    when(userContext.currentWorkspace()).thenReturn(Optional.empty());
    when(userContext.roles()).thenReturn(Set.of(role));
    when(userContext.hasRole(role)).thenReturn(true);
    when(userContext.isStaff()).thenReturn("TECHNICIAN".equals(role) || "ADMIN".equals(role));
  }

  private Room room(RoomStatus status) {
    return rooms.save(Room.builder().code("R-" + UUID.randomUUID().toString().substring(0, 8))
        .building("Nhà A").name("Phòng máy").roomType(RoomType.COMPUTER_LAB).capacity(CAPACITY)
        .status(status).build());
  }

  private Instant at(LocalDate day, int hour, int minute) {
    return day.atTime(hour, minute).atZone(BusinessTime.ZONE).toInstant();
  }

  private BookingResponse book(Room target, Instant from, Instant to, int attendees) {
    return bookings.create(
        new BookingCreateRequest(target.getId(), "Thực hành", attendees, from, to),
        UUID.randomUUID());
  }

  private BookingResponse book(Instant from, Instant to) {
    return book(room, from, to, 20);
  }

  private void assertRefused(Runnable action, String errorCode) {
    assertThatThrownBy(action::run).isInstanceOfSatisfying(PokaYokeViolationException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
  }

  @Test
  void bookingOfExactlyFourHoursIsAcceptedAndLongerIsRefused() {
    actAs(lecturer, "LECTURER");

    assertThat(book(at(tomorrow, 7, 0), at(tomorrow, 11, 0)).status()).isEqualTo("PENDING");

    assertRefused(() -> book(at(tomorrow, 13, 0), at(tomorrow, 17, 30)), "BOOKING_TOO_LONG");
  }

  @Test
  void attendeesAboveTheCapacityAreRefused() {
    actAs(lecturer, "LECTURER");

    assertThat(book(room, at(tomorrow, 7, 0), at(tomorrow, 9, 0), CAPACITY).status())
        .isEqualTo("PENDING");

    assertRefused(() -> book(room, at(tomorrow, 13, 0), at(tomorrow, 15, 0), CAPACITY + 1),
        "ROOM_CAPACITY_EXCEEDED");
  }

  @Test
  void timesMustFollowTheHalfHourStepAndEndAfterTheStart() {
    actAs(lecturer, "LECTURER");

    assertRefused(() -> book(at(tomorrow, 8, 15), at(tomorrow, 9, 30)), "BOOKING_STEP_INVALID");
    assertRefused(() -> book(at(tomorrow, 8, 0), at(tomorrow, 9, 45)), "BOOKING_STEP_INVALID");
    assertRefused(() -> book(at(tomorrow, 9, 0), at(tomorrow, 9, 0)), "BOOKING_RANGE_INVALID");
    assertRefused(() -> book(at(tomorrow, 10, 0), at(tomorrow, 9, 0)), "BOOKING_RANGE_INVALID");
  }

  @Test
  void bookingMustStartInTheFutureAndWithinThirtyDays() {
    actAs(lecturer, "LECTURER");
    LocalDate yesterday = tomorrow.minusDays(2);
    LocalDate farAway = tomorrow.plusDays(40);

    assertRefused(() -> book(at(yesterday, 8, 0), at(yesterday, 10, 0)), "BOOKING_IN_THE_PAST");
    assertRefused(() -> book(at(farAway, 8, 0), at(farAway, 10, 0)), "BOOKING_TOO_FAR");
    assertThat(book(at(tomorrow.plusDays(29), 8, 0), at(tomorrow.plusDays(29), 10, 0)).status())
        .isEqualTo("PENDING");
  }

  @Test
  void roomUnderMaintenanceTakesNoBooking() {
    actAs(lecturer, "LECTURER");
    Room closed = room(RoomStatus.MAINTENANCE);

    assertRefused(() -> book(closed, at(tomorrow, 8, 0), at(tomorrow, 10, 0), 10),
        "ROOM_UNDER_MAINTENANCE");
  }

  @Test
  void overlappingRequestIsRefusedButBackToBackAndOtherRoomsAreNot() {
    actAs(lecturer, "LECTURER");
    book(at(tomorrow, 8, 0), at(tomorrow, 10, 0));

    actAs(otherLecturer, "LECTURER");
    assertRefused(() -> book(at(tomorrow, 9, 30), at(tomorrow, 11, 0)), "ROOM_SLOT_TAKEN");
    assertThat(book(at(tomorrow, 10, 0), at(tomorrow, 12, 0)).status()).isEqualTo("PENDING");
    assertThat(
        book(room(RoomStatus.AVAILABLE), at(tomorrow, 8, 0), at(tomorrow, 10, 0), 20).status())
        .isEqualTo("PENDING");
  }

  @Test
  void sameRequestKeyReturnsTheSameBooking() {
    actAs(lecturer, "LECTURER");
    UUID key = UUID.randomUUID();
    var request = new BookingCreateRequest(room.getId(), "Thực hành", 20, at(tomorrow, 8, 0),
        at(tomorrow, 10, 0));

    BookingResponse first = bookings.create(request, key);
    BookingResponse replay = bookings.create(request, key);

    assertThat(replay.id()).isEqualTo(first.id());
  }

  @Test
  void onlyTechnicianOrAdministratorDecidesAndTheRequesterIsTold() {
    actAs(lecturer, "LECTURER");
    BookingResponse booking = book(at(tomorrow, 8, 0), at(tomorrow, 10, 0));

    assertThatThrownBy(() -> bookings.approve(booking.id()))
        .isInstanceOf(ActionNotAllowedException.class);
    actAs(otherLecturer, "LECTURER");
    assertThatThrownBy(() -> bookings.approve(booking.id()))
        .isInstanceOf(ResourceNotFoundException.class);

    actAs(technician, "TECHNICIAN");
    assertThat(bookings.approve(booking.id()).status()).isEqualTo("APPROVED");

    actAs(lecturer, "LECTURER");
    assertThat(inbox.listMine(false, 0, 10).items()).extracting(item -> item.type())
        .containsExactly("BOOKING_APPROVED");
  }

  @Test
  void rejectionNeedsAReasonAndGivesTheSlotBack() {
    actAs(lecturer, "LECTURER");
    BookingResponse booking = book(at(tomorrow, 8, 0), at(tomorrow, 10, 0));

    actAs(technician, "TECHNICIAN");
    assertRefused(() -> bookings.reject(booking.id(), new BookingRejectRequest(" ")),
        "REJECTION_REASON_REQUIRED");
    BookingResponse rejected =
        bookings.reject(booking.id(), new BookingRejectRequest("Phòng dành cho thi"));
    assertThat(rejected.status()).isEqualTo("REJECTED");
    assertThat(rejected.rejectionReason()).isEqualTo("Phòng dành cho thi");

    actAs(otherLecturer, "LECTURER");
    assertThat(book(at(tomorrow, 8, 0), at(tomorrow, 10, 0)).status()).isEqualTo("PENDING");
    actAs(lecturer, "LECTURER");
    assertThat(inbox.listMine(false, 0, 10).items()).extracting(item -> item.type())
        .containsExactly("BOOKING_REJECTED");
  }

  @Test
  void onlyTheRequesterOrStaffCancelsAndTheSlotIsFreed() {
    actAs(lecturer, "LECTURER");
    BookingResponse booking = book(at(tomorrow, 8, 0), at(tomorrow, 10, 0));

    actAs(otherLecturer, "LECTURER");
    assertThatThrownBy(() -> bookings.cancel(booking.id()))
        .isInstanceOf(ResourceNotFoundException.class);

    actAs(lecturer, "LECTURER");
    assertThat(bookings.cancel(booking.id()).status()).isEqualTo("CANCELLED");
    assertThatThrownBy(() -> bookings.cancel(booking.id()))
        .isInstanceOf(InvalidStateException.class);

    actAs(otherLecturer, "LECTURER");
    assertThat(book(at(tomorrow, 8, 0), at(tomorrow, 10, 0)).status()).isEqualTo("PENDING");
  }

  @Test
  void lecturerSeesOnlyOwnBookingsAndTheScheduleHidesOtherNames() {
    actAs(lecturer, "LECTURER");
    BookingResponse mine = book(at(tomorrow, 8, 0), at(tomorrow, 10, 0));
    actAs(otherLecturer, "LECTURER");
    BookingResponse theirs = book(at(tomorrow, 13, 0), at(tomorrow, 15, 0));

    actAs(lecturer, "LECTURER");
    assertThat(bookings.list(null, room.getId(), 0, 50).items()).extracting(BookingResponse::id)
        .containsExactly(mine.id());
    assertThatThrownBy(() -> bookings.get(theirs.id()))
        .isInstanceOf(ResourceNotFoundException.class);
    var entries = bookings.schedule(room.getId(), tomorrow, tomorrow).entries();
    assertThat(entries).hasSize(2);
    assertThat(entries).filteredOn(e -> e.bookingId().equals(mine.id()))
        .extracting(e -> e.requesterName()).doesNotContainNull();
    assertThat(entries).filteredOn(e -> e.bookingId().equals(theirs.id()))
        .extracting(e -> e.requesterName()).containsOnlyNulls();

    actAs(technician, "TECHNICIAN");
    assertThat(bookings.list(null, room.getId(), 0, 50).items()).extracting(BookingResponse::id)
        .containsExactlyInAnyOrder(mine.id(), theirs.id());
    assertThat(bookings.schedule(room.getId(), tomorrow, tomorrow).entries())
        .extracting(e -> e.requesterName()).doesNotContainNull();
  }
}
