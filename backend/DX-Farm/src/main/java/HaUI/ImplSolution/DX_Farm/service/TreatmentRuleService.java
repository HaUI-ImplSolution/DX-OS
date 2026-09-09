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
    return treatmentRuleRepository.findAll().stream().map(TreatmentRuleService::toResponse)
        .toList();
  }

  public TreatmentRule findByDiseaseCodeOrThrow(String diseaseCode) {
    return treatmentRuleRepository.findFirstByDisease_Code(diseaseCode).orElseThrow(
        () -> new ResourceNotFoundException("No treatment rule found for disease: " + diseaseCode));
  }

  public static TreatmentRuleResponse toResponse(TreatmentRule rule) {
    return new TreatmentRuleResponse(rule.getId(), rule.getDisease().getCode(),
        rule.getPesticide().getCode(), rule.getDoseAmount(), rule.getDoseUnit(), rule.getPhiDays(),
        rule.getNotes(), rule.getSource(), rule.getVersion());
  }
}
