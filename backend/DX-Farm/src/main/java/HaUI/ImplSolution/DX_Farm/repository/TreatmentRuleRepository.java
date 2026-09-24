package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.TreatmentRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentRuleRepository extends JpaRepository<TreatmentRule, Long> {

  List<TreatmentRule> findByDisease_CodeOrderByIdAsc(String diseaseCode);
}
