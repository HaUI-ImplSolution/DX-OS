package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.core.identity.Household;
import HaUI.ImplSolution.DX_Farm.model.Parcel;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParcelRepository extends JpaRepository<Parcel, Long> {

  Optional<Parcel> findByCodeAndDeletedAtIsNull(String code);

  boolean existsByCodeAndDeletedAtIsNull(String code);

  List<Parcel> findByDeletedAtIsNullOrderByIdAsc();

  List<Parcel> findByHousehold_IdAndDeletedAtIsNullOrderByIdAsc(Long householdId);

  List<Parcel> findAllByOrderByIdAsc();

  Optional<Parcel> findByClientRequestId(UUID clientRequestId);

  List<Parcel> findByHousehold_Code(String householdCode);

  List<Parcel> findByIdIn(Collection<Long> ids);
}
