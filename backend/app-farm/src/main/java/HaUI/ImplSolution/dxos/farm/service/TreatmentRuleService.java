// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.farm.dto.TreatmentRuleResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.farm.model.TreatmentRule;
import HaUI.ImplSolution.dxos.farm.repository.TreatmentRuleRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TreatmentRuleService {

  private final TreatmentRuleRepository treatmentRuleRepository;

  public TreatmentRuleService(TreatmentRuleRepository treatmentRuleRepository) {
    this.treatmentRuleRepository = treatmentRuleRepository;
  }

  public List<TreatmentRuleResponse> listAll() {
    return treatmentRuleRepository.findAllByActiveTrueOrderByIdAsc().stream()
        .map(TreatmentRuleService::toResponse).toList();
  }

  public Optional<TreatmentRule> findByDiseaseCode(String code) {
    return treatmentRuleRepository.findFirstByDisease_CodeAndActiveTrueOrderByIdAsc(code);
  }

  public TreatmentRule findByDiseaseCodeOrThrow(String diseaseCode) {
    return treatmentRuleRepository.findFirstByDisease_CodeAndActiveTrueOrderByIdAsc(diseaseCode)
        .orElseThrow(() -> new ResourceNotFoundException(
            "No treatment rule found for disease: " + diseaseCode));
  }

  public TreatmentRule findByIdAndDiseaseCodeOrThrow(Long ruleId, String diseaseCode) {
    return treatmentRuleRepository.findByIdAndDisease_CodeAndActiveTrue(ruleId, diseaseCode)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Treatment rule %d not found for disease: %s".formatted(ruleId, diseaseCode)));
  }

  public static TreatmentRuleResponse toResponse(TreatmentRule rule) {
    return new TreatmentRuleResponse(rule.getId(), rule.getDisease().getCode(),
        rule.getPesticide().getCode(), rule.getDoseMin(), rule.getDoseMax(), rule.getDoseUnit(),
        rule.getPhiDays(), rule.getNotes(), rule.getSource(), rule.getVersion());
  }
}
