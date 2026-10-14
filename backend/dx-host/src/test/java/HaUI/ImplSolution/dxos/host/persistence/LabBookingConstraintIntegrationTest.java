// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import HaUI.ImplSolution.dxos.lab.model.Room;
import HaUI.ImplSolution.dxos.lab.model.RoomBooking;
import HaUI.ImplSolution.dxos.lab.model.RoomType;
import HaUI.ImplSolution.dxos.lab.repository.RoomBookingRepository;
import HaUI.ImplSolution.dxos.lab.repository.RoomRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// The room booking exclusion constraint is what makes simultaneous requests safe, so it is
// exercised on the migrated schema rather than through the service.
@DataJpaTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(PersistenceTestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LabBookingConstraintIntegrationTest {

  private static final Instant START = Instant.parse("2026-11-02T01:00:00Z");

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
  RoomRepository rooms;
  @Autowired
  RoomBookingRepository bookings;

  private Room room() {
    return rooms.saveAndFlush(
        Room.builder().code("R-" + UUID.randomUUID().toString().substring(0, 8)).building("Nhà A")
            .name("Phòng máy").roomType(RoomType.COMPUTER_LAB).capacity(30).build());
  }

  private RoomBooking booking(Room room, Instant from, Duration length, boolean active) {
    return RoomBooking.builder().room(room).requesterId("lecturer-1").requesterName("Lecturer")
        .requesterRole("LECTURER").title("Thực hành").attendees(20).startsAt(from)
        .endsAt(from.plus(length)).active(active).status(active ? "PENDING" : "CANCELLED").build();
  }

  @Test
  void overlappingActiveBookingOfTheSameRoomIsRefusedByTheDatabase() {
    Room room = room();
    bookings.saveAndFlush(booking(room, START, Duration.ofHours(2), true));

    assertThatThrownBy(() -> bookings
        .saveAndFlush(booking(room, START.plus(Duration.ofHours(1)), Duration.ofHours(2), true)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void backToBackBookingsAndOtherRoomsDoNotConflict() {
    Room room = room();
    Room other = room();
    bookings.saveAndFlush(booking(room, START, Duration.ofHours(2), true));

    bookings
        .saveAndFlush(booking(room, START.plus(Duration.ofHours(2)), Duration.ofHours(2), true));
    bookings.saveAndFlush(booking(other, START, Duration.ofHours(2), true));

    assertThat(bookings.findAll()).filteredOn(b -> b.getRoom().getId().equals(room.getId()))
        .hasSize(2);
  }

  @Test
  void cancelledBookingReleasesItsSlot() {
    Room room = room();
    bookings.saveAndFlush(booking(room, START, Duration.ofHours(2), false));

    RoomBooking replacement =
        bookings.saveAndFlush(booking(room, START, Duration.ofHours(2), true));

    assertThat(replacement.getId()).isNotNull();
  }
}
