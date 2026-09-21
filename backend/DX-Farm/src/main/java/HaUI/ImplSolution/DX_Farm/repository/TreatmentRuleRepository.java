package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.TreatmentRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentRuleRepository extends JpaRepository<TreatmentRule, Long> {

  List<TreatmentRule> findByDisease_CodeOrderByIdAsc(String diseaseCode);

  Optional<TreatmentRule> findFirstByDisease_CodeOrderByIdAsc(String diseaseCode);

  Optional<TreatmentRule> findByIdAndDisease_Code(Long id, String diseaseCode);
}
