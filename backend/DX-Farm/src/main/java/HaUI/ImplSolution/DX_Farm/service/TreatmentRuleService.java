package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.TreatmentRuleResponse;
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

  public List<TreatmentRule> findByDiseaseCode(String diseaseCode) {
    return treatmentRuleRepository.findByDisease_CodeOrderByIdAsc(diseaseCode);
  }

  public static TreatmentRuleResponse toResponse(TreatmentRule rule) {
    return new TreatmentRuleResponse(rule.getId(), rule.getDisease().getCode(),
        rule.getPesticide().getCode(), rule.getDoseMin(), rule.getDoseMax(), rule.getDoseUnit(),
        rule.getPhiDays(), rule.getNotes(), rule.getSource(), rule.getVersion());
  }
}
