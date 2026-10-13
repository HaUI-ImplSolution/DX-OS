// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.repository;

import HaUI.ImplSolution.dxos.lab.model.DeviceMaintenance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceMaintenanceRepository extends JpaRepository<DeviceMaintenance, Long> {

  List<DeviceMaintenance> findByDevice_IdOrderByStartedAtDescIdDesc(Long deviceId);

  Optional<DeviceMaintenance> findByDevice_IdAndCompletedAtIsNull(Long deviceId);

  Optional<DeviceMaintenance> findByIdAndDevice_Id(Long id, Long deviceId);
}
