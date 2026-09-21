// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.repository;

import HaUI.ImplSolution.dxos.farm.model.Parcel;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParcelRepository extends JpaRepository<Parcel, Long> {

  Optional<Parcel> findByCodeAndDeletedAtIsNull(String code);

  boolean existsByCodeAndDeletedAtIsNull(String code);

  List<Parcel> findByDeletedAtIsNullOrderByIdAsc();

  List<Parcel> findByHouseholdIdAndDeletedAtIsNullOrderByIdAsc(Long householdId);

  List<Parcel> findAllByOrderByIdAsc();

  Optional<Parcel> findByClientRequestId(UUID clientRequestId);

  List<Parcel> findByHouseholdId(Long householdId);

  List<Parcel> findByIdIn(Collection<Long> ids);
}
