package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.TreatmentRule;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentRuleRepository extends JpaRepository<TreatmentRule, Long> {

  Optional<TreatmentRule> findFirstByDisease_Code(String diseaseCode);
}
