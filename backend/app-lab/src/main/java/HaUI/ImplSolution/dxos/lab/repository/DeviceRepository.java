// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.repository;

import HaUI.ImplSolution.dxos.lab.model.Device;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository
    extends JpaRepository<Device, Long>, JpaSpecificationExecutor<Device> {

  boolean existsByAssetCode(String assetCode);

  Optional<Device> findByAssetCode(String assetCode);

  // Serialises loans and maintenance of one device.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from Device d where d.id = :id")
  Optional<Device> findByIdForUpdate(@Param("id") Long id);
}
