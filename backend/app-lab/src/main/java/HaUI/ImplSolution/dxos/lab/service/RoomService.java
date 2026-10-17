// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.lab.dto.BookingDtos.BookingResponse;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.RoomRequest;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.RoomResponse;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.RoomStatusChangeResponse;
import HaUI.ImplSolution.dxos.lab.model.Room;
import HaUI.ImplSolution.dxos.lab.model.RoomStatus;
import HaUI.ImplSolution.dxos.lab.model.RoomType;
import HaUI.ImplSolution.dxos.lab.repository.RoomBookingRepository;
import HaUI.ImplSolution.dxos.lab.repository.RoomRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Rooms that can be booked and hold devices.
@Service
@Transactional(readOnly = true)
public class RoomService {

  private final RoomRepository roomRepository;
  private final RoomBookingRepository bookingRepository;
  private final Clock clock;

  public RoomService(RoomRepository roomRepository, RoomBookingRepository bookingRepository,
      Clock clock) {
    this.roomRepository = roomRepository;
    this.bookingRepository = bookingRepository;
    this.clock = clock;
  }

  public List<RoomResponse> list(RoomType type, RoomStatus status) {
    List<Room> rooms = type != null ? roomRepository.findByRoomTypeOrderByCodeAsc(type)
        : roomRepository.findAllByOrderByCodeAsc();
    return rooms.stream().filter(room -> status == null || room.getStatus() == status)
        .map(RoomResponse::of).toList();
  }

  public RoomResponse get(Long id) {
    return RoomResponse.of(findOrThrow(id));
  }

  @Transactional
  public RoomResponse create(RoomRequest request) {
    String code = request.code().strip();
    if (roomRepository.existsByCode(code)) {
      throw new PokaYokeViolationException("ROOM_CODE_TAKEN",
          "Mã phòng %s đã tồn tại".formatted(code));
    }
    Room room =
        Room.builder().code(code).building(request.building().strip()).name(request.name().strip())
            .roomType(request.roomType()).capacity(request.capacity()).build();
    return RoomResponse.of(roomRepository.save(room));
  }

  @Transactional
  public RoomResponse update(Long id, RoomRequest request) {
    Room room = findOrThrow(id);
    String code = request.code().strip();
    if (!room.getCode().equals(code) && roomRepository.existsByCode(code)) {
      throw new PokaYokeViolationException("ROOM_CODE_TAKEN",
          "Mã phòng %s đã tồn tại".formatted(code));
    }
    room.setCode(code);
    room.setBuilding(request.building().strip());
    room.setName(request.name().strip());
    room.setRoomType(request.roomType());
    room.setCapacity(request.capacity());
    return RoomResponse.of(roomRepository.save(room));
  }

  // Putting a room under maintenance leaves its live bookings untouched and returns them, so the
  // manager can decide what to do with each.
  @Transactional
  public RoomStatusChangeResponse changeStatus(Long id, RoomStatus status) {
    Room room = findOrThrow(id);
    room.setStatus(status);
    roomRepository.save(room);
    List<BookingResponse> affected = status == RoomStatus.MAINTENANCE ? bookingRepository
        .findByRoom_IdAndActiveTrueAndEndsAtGreaterThanOrderByStartsAtAsc(id, clock.instant())
        .stream().map(BookingResponse::of).toList() : List.of();
    return new RoomStatusChangeResponse(RoomResponse.of(room), affected);
  }

  Room findOrThrow(Long id) {
    return roomRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + id));
  }
}
