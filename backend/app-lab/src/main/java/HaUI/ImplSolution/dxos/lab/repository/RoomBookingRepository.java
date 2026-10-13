// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.repository;

import HaUI.ImplSolution.dxos.lab.model.RoomBooking;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RoomBookingRepository
    extends JpaRepository<RoomBooking, Long>, JpaSpecificationExecutor<RoomBooking> {

  @Override
  @EntityGraph(attributePaths = {"room"})
  Page<RoomBooking> findAll(Specification<RoomBooking> spec, Pageable pageable);

  Optional<RoomBooking> findByClientRequestId(UUID clientRequestId);

  List<RoomBooking> findByIdIn(Collection<Long> ids);

  List<RoomBooking> findByWorkspaceId(Long workspaceId);

  // Bookings that hold the room at some moment of [from, to).
  List<RoomBooking> findByRoom_IdAndActiveTrueAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAsc(
      Long roomId, Instant to, Instant from);

  boolean existsByRoom_IdAndActiveTrueAndStartsAtLessThanAndEndsAtGreaterThan(Long roomId,
      Instant endsAt, Instant startsAt);

  // Live bookings of a room that have not ended yet.
  List<RoomBooking> findByRoom_IdAndActiveTrueAndEndsAtGreaterThanOrderByStartsAtAsc(Long roomId,
      Instant now);
}
