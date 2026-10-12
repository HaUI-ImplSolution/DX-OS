// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.farm.dto.AiDiagnosisContextResponse;
import HaUI.ImplSolution.dxos.farm.dto.AiDiagnosisContextResponse.RecentTreatment;
import HaUI.ImplSolution.dxos.farm.dto.AiDiagnosisContextResponse.RuleFact;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.farm.model.Disease;
import HaUI.ImplSolution.dxos.farm.model.TreatmentApplication;
import HaUI.ImplSolution.dxos.farm.model.TreatmentConfirmationStatus;
import HaUI.ImplSolution.dxos.farm.model.TreatmentRule;
import HaUI.ImplSolution.dxos.farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.dxos.farm.repository.DiseaseRepository;
import HaUI.ImplSolution.dxos.farm.repository.TreatmentApplicationRepository;
import HaUI.ImplSolution.dxos.farm.repository.TreatmentRuleRepository;
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
        treatmentApplicationRepository
            .findByCropSeasonIdAndConfirmationStatusNotOrderByAppliedAtDesc(cropSeasonId,
                TreatmentConfirmationStatus.REJECTED)
            .stream().limit(RECENT_TREATMENTS_LIMIT).toList();

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
