package HaUI.ImplSolution.DX_Farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.DX_Farm.core.storage.StorageService;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowService;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisApplyRequest;
import HaUI.ImplSolution.DX_Farm.exception.PokaYokeViolationException;
import HaUI.ImplSolution.DX_Farm.model.ApprovalStatus;
import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.Diagnosis;
import HaUI.ImplSolution.DX_Farm.model.DiseaseReport;
import HaUI.ImplSolution.DX_Farm.model.Parcel;
import HaUI.ImplSolution.DX_Farm.model.Pesticide;
import HaUI.ImplSolution.DX_Farm.model.TreatmentApplication;
import HaUI.ImplSolution.DX_Farm.model.TreatmentRule;
import HaUI.ImplSolution.DX_Farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiseaseReportRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiseaseRepository;
import HaUI.ImplSolution.DX_Farm.repository.TreatmentApplicationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class DiagnosisServiceApplyTest {

  // 18:00 UTC on 6 Oct is 01:00 on 7 Oct in Vietnam, so "today" is 2026-10-07.
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-06T18:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

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
  private WorkflowService workflowService;
  @Mock
  private StorageService storageService;
  @Mock
  private AiDiagnosisClient aiDiagnosisClient;
  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private EntityManager entityManager;

  private DiagnosisService service;
  private CropSeason season;

  @BeforeEach
  void setUp() {
    service = new DiagnosisService(diagnosisRepository, diseaseReportRepository, diseaseRepository,
        treatmentRuleService, treatmentApplicationRepository, currentUserService, workflowService,
        aiDiagnosisClient, eventPublisher, storageService, CLOCK, entityManager);
    Parcel parcel = Parcel.builder().id(1L).code("P-01").farmerName("A").build();
    season = CropSeason.builder().id(5L).parcel(parcel).build();
    DiseaseReport report = DiseaseReport.builder().id(9L).cropSeason(season).build();
    TreatmentRule rule = TreatmentRule.builder().pesticide(Pesticide.builder().code("X").build())
        .doseMin(new BigDecimal("1.00")).doseMax(new BigDecimal("2.00")).doseUnit("L/ha")
        .phiDays(14).build();
    Diagnosis diagnosis = Diagnosis.builder().id(3L).diseaseReport(report).treatmentRule(rule)
        .approvalStatus(ApprovalStatus.APPROVED).build();
    when(diseaseReportRepository.findById(9L)).thenReturn(Optional.of(report));
    when(diagnosisRepository.findByDiseaseReportId(9L)).thenReturn(Optional.of(diagnosis));
    when(treatmentApplicationRepository.findByDiagnosisId(3L)).thenReturn(Optional.empty());
  }

  private DiagnosisApplyRequest request(LocalDate appliedAt) {
    return new DiagnosisApplyRequest(new BigDecimal("1.50"), appliedAt);
  }

  @Test
  void applyRejectsFutureDate() {
    assertThatThrownBy(() -> service.apply(9L, request(TODAY.plusDays(1)))).isInstanceOfSatisfying(
        PokaYokeViolationException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo("INVALID_APPLIED_AT"));
    verify(treatmentApplicationRepository, never()).saveAndFlush(any());
  }

  @Test
  void applyRejectsDateOlderThanThirtyDays() {
    assertThatThrownBy(() -> service.apply(9L, request(TODAY.minusDays(31))))
        .isInstanceOfSatisfying(PokaYokeViolationException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("INVALID_APPLIED_AT"));
    verify(treatmentApplicationRepository, never()).saveAndFlush(any());
  }

  @Test
  void applyAcceptsBoundaryDateAndComputesLockFromIt() {
    when(treatmentApplicationRepository.saveAndFlush(any(TreatmentApplication.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.apply(9L, request(TODAY.minusDays(30)));

    assertThat(response.appliedAt()).isEqualTo(TODAY.minusDays(30));
    assertThat(response.harvestLockedUntil()).isEqualTo(TODAY.minusDays(30).plusDays(14));
    verify(entityManager).lock(season, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
  }

  @Test
  void applyDefaultsToVietnamTodayAndComputesLock() {
    when(treatmentApplicationRepository.saveAndFlush(any(TreatmentApplication.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.apply(9L, request(null));

    assertThat(response.appliedAt()).isEqualTo(TODAY);
    assertThat(response.harvestLockedUntil()).isEqualTo(TODAY.plusDays(14));
  }
}
