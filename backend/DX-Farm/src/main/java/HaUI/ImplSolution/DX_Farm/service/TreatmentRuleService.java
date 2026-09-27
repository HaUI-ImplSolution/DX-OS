package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.TreatmentRuleResponse;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.TreatmentRule;
import HaUI.ImplSolution.DX_Farm.repository.TreatmentRuleRepository;
import java.util.List;
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
