package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.Household;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HouseholdRepository extends JpaRepository<Household, Long> {

  Optional<Household> findByCode(String code);

  Optional<Household> findByKeycloakUserId(String keycloakUserId);

  List<Household> findAllByOrderByCodeAsc();

  @Query(value = "SELECT nextval('household_code_seq')", nativeQuery = true)
  long nextCodeNumber();
}
