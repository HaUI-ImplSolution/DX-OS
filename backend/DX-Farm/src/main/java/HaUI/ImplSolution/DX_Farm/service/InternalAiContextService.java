package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.AiDiagnosisContextResponse;
import HaUI.ImplSolution.DX_Farm.dto.AiDiagnosisContextResponse.RecentTreatment;
import HaUI.ImplSolution.DX_Farm.dto.AiDiagnosisContextResponse.RuleFact;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.Disease;
import HaUI.ImplSolution.DX_Farm.model.TreatmentApplication;
import HaUI.ImplSolution.DX_Farm.model.TreatmentRule;
import HaUI.ImplSolution.DX_Farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiseaseRepository;
import HaUI.ImplSolution.DX_Farm.repository.TreatmentApplicationRepository;
import HaUI.ImplSolution.DX_Farm.repository.TreatmentRuleRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InternalAiContextService {

  private static final int RECENT_TREATMENTS_LIMIT = 5;

  private final DiseaseRepository diseaseRepository;
  private final TreatmentRuleRepository treatmentRuleRepository;
  private final TreatmentApplicationRepository treatmentApplicationRepository;
  private final CropSeasonRepository cropSeasonRepository;

  public InternalAiContextService(DiseaseRepository diseaseRepository,
      TreatmentRuleRepository treatmentRuleRepository,
      TreatmentApplicationRepository treatmentApplicationRepository,
      CropSeasonRepository cropSeasonRepository) {
    this.diseaseRepository = diseaseRepository;
    this.treatmentRuleRepository = treatmentRuleRepository;
    this.treatmentApplicationRepository = treatmentApplicationRepository;
    this.cropSeasonRepository = cropSeasonRepository;
  }

  public AiDiagnosisContextResponse getDiagnosisContext(String requestId, String diseaseCode,
      Long cropSeasonId) {
    if (!cropSeasonRepository.existsById(cropSeasonId)) {
      throw new ResourceNotFoundException("Crop season not found: " + cropSeasonId);
    }
    Disease disease = diseaseRepository.findByCodeAndActiveTrue(diseaseCode)
        .orElseThrow(() -> new ResourceNotFoundException("Unknown disease code: " + diseaseCode));
    List<RuleFact> rules =
        treatmentRuleRepository.findByDisease_CodeAndActiveTrueOrderByIdAsc(diseaseCode).stream()
            .map(InternalAiContextService::toRuleFact).toList();
    List<TreatmentApplication> recentTreatments =
        treatmentApplicationRepository.findByCropSeasonIdOrderByAppliedAtDesc(cropSeasonId).stream()
            .limit(RECENT_TREATMENTS_LIMIT).toList();

    return new AiDiagnosisContextResponse(requestId, disease.getCode(), disease.getName(), rules,
        recentTreatments.stream().map(InternalAiContextService::toRecentTreatment).toList());
  }

  private static RuleFact toRuleFact(TreatmentRule rule) {
    return new RuleFact(String.valueOf(rule.getId()), rule.getPesticide().getName(),
        rule.getDoseMin(), rule.getDoseMax(), rule.getDoseUnit(), rule.getPhiDays(),
        rule.getVersion());
  }

  private static RecentTreatment toRecentTreatment(TreatmentApplication application) {
    return new RecentTreatment(application.getPesticide().getName(), application.getAppliedAt(),
        application.getHarvestLockedUntil());
  }
}
