// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.repository;

import HaUI.ImplSolution.dxos.farm.model.TreatmentRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentRuleRepository extends JpaRepository<TreatmentRule, Long> {

  List<TreatmentRule> findAllByActiveTrueOrderByIdAsc();

  List<TreatmentRule> findByDisease_CodeAndActiveTrueOrderByIdAsc(String diseaseCode);

  Optional<TreatmentRule> findFirstByDisease_CodeAndActiveTrueOrderByIdAsc(String diseaseCode);

  Optional<TreatmentRule> findByIdAndDisease_CodeAndActiveTrue(Long id, String diseaseCode);
}
