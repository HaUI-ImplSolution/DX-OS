// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.DX_Farm.dto.AiDiagnoseResponse;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisCreateRequest;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.Diagnosis;
import HaUI.ImplSolution.DX_Farm.model.Disease;
import HaUI.ImplSolution.DX_Farm.model.DiseaseReport;
import HaUI.ImplSolution.DX_Farm.model.Parcel;
import HaUI.ImplSolution.DX_Farm.model.Pesticide;
import HaUI.ImplSolution.DX_Farm.model.TreatmentRule;
import HaUI.ImplSolution.DX_Farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiseaseRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiseaseReportRepository;
import HaUI.ImplSolution.DX_Farm.repository.TreatmentApplicationRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DiagnosisServiceTest {

  private static final long REPORT_ID = 10L;
  private static final String DISEASE_CODE = "Tomato___Late_blight";

  @Mock
  private DiagnosisRepository diagnosisRepository;
  @Mock
  private DiseaseReportRepository diseaseReportRepository;
  @Mock
  private DiseaseRepository diseaseRepository;
  @Mock
  private TreatmentRuleService treatmentRuleService;
  @Mock
  private TreatmentApplicationRepository treatmentApplicationRepository;
  @Mock
  private CurrentUserService currentUserService;
  @Mock
  private AiDiagnosisClient aiDiagnosisClient;

  private DiagnosisService service;
  private Disease disease;
  private DiseaseReport report;

  @BeforeEach
  void setUp() {
    disease = Disease.builder().id(3L).code(DISEASE_CODE).name("Bệnh mốc sương").build();
    Parcel parcel = Parcel.builder().id(1L).code("P-01").farmerName("Farmer").build();
    CropSeason season = CropSeason.builder().id(2L).parcel(parcel).riceVariety("Cà chua").build();
    report = DiseaseReport.builder().id(REPORT_ID).cropSeason(season).imageRef("leaf.png")
        .reportedBy("Farmer").build();
    service = new DiagnosisService(diagnosisRepository, diseaseReportRepository, diseaseRepository,
        treatmentRuleService, treatmentApplicationRepository, currentUserService, aiDiagnosisClient,
        "http://backend:8081", new BigDecimal("0.70"));

    when(diseaseReportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
    when(diagnosisRepository.findByDiseaseReportId(REPORT_ID)).thenReturn(Optional.empty());
    when(diseaseRepository.findByCode(DISEASE_CODE)).thenReturn(Optional.of(disease));
    lenient().when(treatmentApplicationRepository.findByCropSeasonIdOrderByAppliedAtDesc(2L))
        .thenReturn(List.of());
    lenient().when(diagnosisRepository.saveAndFlush(any(Diagnosis.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void rejectsTreatmentSelectedBelowConfidenceThreshold() {
    TreatmentRule rule = rule(7L, "v1");
    when(treatmentRuleService.findByDiseaseCode(DISEASE_CODE)).thenReturn(List.of(rule));
    when(aiDiagnosisClient.diagnose(any()))
        .thenReturn(aiResponse("0.69", "treatment", List.of(ruleFact(rule)), "7"));

    assertThatThrownBy(() -> service.createFromAi(REPORT_ID))
        .isInstanceOf(InvalidStateException.class).hasMessageContaining("Low-confidence");
    verify(diagnosisRepository, never()).saveAndFlush(any());
  }

  @Test
  void keepsMultipleRulesOnReviewPathWithoutSelectingOne() {
    TreatmentRule first = rule(7L, "v1");
    TreatmentRule second = rule(8L, "v2");
    when(treatmentRuleService.findByDiseaseCode(DISEASE_CODE)).thenReturn(List.of(first, second));
    when(aiDiagnosisClient.diagnose(any())).thenReturn(
        aiResponse("0.90", "review_required", List.of(ruleFact(first), ruleFact(second)), null));

    var response = service.createFromAi(REPORT_ID);

    assertThat(response.recommendationPath()).isEqualTo("review_required");
    assertThat(response.treatmentRule()).isNull();
  }

  @Test
  void acceptsTheOnlyRuleAtExactConfidenceThreshold() {
    TreatmentRule rule = rule(7L, "v1");
    when(treatmentRuleService.findByDiseaseCode(DISEASE_CODE)).thenReturn(List.of(rule));
    when(aiDiagnosisClient.diagnose(any()))
        .thenReturn(aiResponse("0.70", "treatment", List.of(ruleFact(rule)), "7"));

    var response = service.createFromAi(REPORT_ID);

    assertThat(response.recommendationPath()).isEqualTo("treatment");
    assertThat(response.treatmentRule().id()).isEqualTo(7L);
  }

  @Test
  void manualDiagnosisDoesNotSelectTheFirstOfMultipleRules() {
    TreatmentRule first = rule(7L, "v1");
    TreatmentRule second = rule(8L, "v2");
    when(treatmentRuleService.findByDiseaseCode(DISEASE_CODE)).thenReturn(List.of(first, second));

    var response = service.create(REPORT_ID,
        new DiagnosisCreateRequest(DISEASE_CODE, new BigDecimal("0.90"), "manual-v1"));

    assertThat(response.recommendationPath()).isEqualTo("review_required");
    assertThat(response.treatmentRule()).isNull();
    assertThat(response.recommendationText()).contains("không tự chọn thuốc");
  }

  @Test
  void mapsConcurrentDuplicateDiagnosisToControlledConflict() {
    TreatmentRule rule = rule(7L, "v1");
    when(treatmentRuleService.findByDiseaseCode(DISEASE_CODE)).thenReturn(List.of(rule));
    when(diagnosisRepository.saveAndFlush(any(Diagnosis.class)))
        .thenThrow(new DataIntegrityViolationException("unique constraint"));

    assertThatThrownBy(() -> service.create(REPORT_ID,
        new DiagnosisCreateRequest(DISEASE_CODE, new BigDecimal("0.90"), "manual-v1")))
        .isInstanceOf(InvalidStateException.class)
        .hasMessage("Disease report already has a diagnosis: 10");
  }

  private TreatmentRule rule(Long id, String version) {
    Pesticide pesticide =
        Pesticide.builder().id(id).code("PEST-" + id).name("Thuốc " + id).unit("kg/ha").build();
    return TreatmentRule.builder().id(id).disease(disease).pesticide(pesticide)
        .doseMin(new BigDecimal("1.00")).doseMax(new BigDecimal("1.50")).doseUnit("kg/ha")
        .phiDays(7).source("Reviewed source").version(version).build();
  }

  private static AiDiagnoseResponse.RuleFact ruleFact(TreatmentRule rule) {
    return new AiDiagnoseResponse.RuleFact(rule.getId().toString(), rule.getPesticide().getName(),
        rule.getDoseMin(), rule.getDoseMax(), rule.getDoseUnit(), rule.getPhiDays(),
        rule.getVersion());
  }

  private static AiDiagnoseResponse aiResponse(String confidence, String path,
      List<AiDiagnoseResponse.RuleFact> rules, String selectedRuleId) {
    return new AiDiagnoseResponse("req-1",
        new AiDiagnoseResponse.CvResult(DISEASE_CODE, "Bệnh mốc sương", new BigDecimal(confidence),
            "EfficientNet-B4", "v1.0"),
        List.of(), new AiDiagnoseResponse.StructuredContext(path, rules),
        new AiDiagnoseResponse.Recommendation(selectedRuleId, "Grounded summary", true));
  }
}
