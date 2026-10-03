package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.core.identity.Household;
import HaUI.ImplSolution.DX_Farm.model.Parcel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParcelRepository extends JpaRepository<Parcel, Long> {

  Optional<Parcel> findByCodeAndDeletedAtIsNull(String code);

  boolean existsByCodeAndDeletedAtIsNull(String code);

  List<Parcel> findByDeletedAtIsNullOrderByIdAsc();

  List<Parcel> findByHousehold_IdAndDeletedAtIsNullOrderByIdAsc(Long householdId);

  List<Parcel> findAllByOrderByIdAsc();
}
