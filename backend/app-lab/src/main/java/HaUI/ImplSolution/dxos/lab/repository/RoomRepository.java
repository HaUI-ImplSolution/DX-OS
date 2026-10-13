// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.repository;

import HaUI.ImplSolution.dxos.lab.model.Room;
import HaUI.ImplSolution.dxos.lab.model.RoomStatus;
import HaUI.ImplSolution.dxos.lab.model.RoomType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

  Optional<Room> findByCode(String code);

  boolean existsByCode(String code);

  List<Room> findAllByOrderByCodeAsc();

  List<Room> findByRoomTypeOrderByCodeAsc(RoomType roomType);

  List<Room> findByStatusOrderByCodeAsc(RoomStatus status);
}
