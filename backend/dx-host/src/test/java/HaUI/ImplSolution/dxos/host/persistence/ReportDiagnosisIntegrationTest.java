// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import HaUI.ImplSolution.dxos.core.config.TimeConfig;
import HaUI.ImplSolution.dxos.core.identity.Workspace;
import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.core.identity.WorkspaceDirectoryService;
import HaUI.ImplSolution.dxos.core.workflow.WorkflowService;
import HaUI.ImplSolution.dxos.core.workflow.PendingRequestLimiter;
import HaUI.ImplSolution.dxos.core.notification.NotificationInboxService;
import HaUI.ImplSolution.dxos.core.notification.NotificationTypes;
import HaUI.ImplSolution.dxos.farm.FarmNotificationConfig;
import HaUI.ImplSolution.dxos.core.workflow.WorkflowDefinitions;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.farm.FarmAppModule;
import HaUI.ImplSolution.dxos.farm.FarmWorkflowConfig;
import HaUI.ImplSolution.dxos.core.workflow.WorkflowCaseRepository;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowStatus;
import HaUI.ImplSolution.dxos.core.api.dto.*;
import HaUI.ImplSolution.dxos.farm.dto.*;
import HaUI.ImplSolution.dxos.core.api.exception.*;
import HaUI.ImplSolution.dxos.farm.exception.*;
import HaUI.ImplSolution.dxos.farm.model.*;
import HaUI.ImplSolution.dxos.farm.repository.*;
import HaUI.ImplSolution.dxos.farm.service.*;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(properties = {"app.public-base-url=http://backend:8081", "spring.flyway.enabled=false",
    "spring.datasource.hikari.maximum-pool-size=1"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({DiagnosisService.class, DiseaseReportService.class, TreatmentRuleService.class,
    CropSeasonService.class, SeasonCalendar.class, PendingRequestLimiter.class, FarmAppModule.class,
    WorkflowService.class, WorkflowDefinitions.class, FarmWorkflowConfig.class,
    NotificationService.class, NotificationInboxService.class, NotificationTypes.class,
    FarmNotificationConfig.class, WorkspaceDirectoryService.class, TimeConfig.class,
    PersistenceTestConfig.class, InternalAiContextService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReportDiagnosisIntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    MigratedDatabase.migrate(POSTGRES);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired
  CropSeasonService seasons;
  @Autowired
  DiagnosisService diagnoses;
  @Autowired
  DiseaseReportService reports;
  @Autowired
  DiagnosisRepository diagnosisRepository;
  @Autowired
  DiseaseReportRepository reportRepository;
  @Autowired
  TrainingLabelRepository labelRepository;
  @Autowired
  EntityManager em;
  @Autowired
  PlatformTransactionManager transactionManager;
  @Autowired
  DataSource dataSource;
  @Autowired
  InternalAiContextService context;
  @MockitoBean
  AiDiagnosisClient ai;
  @MockitoBean
  CurrentUser user;
  @MockitoBean
  UserContext userContext;
  @MockitoBean
  FileStorageService storage;
  @MockitoBean
  ObjectStorage objectStorage;
  @Autowired
  WorkflowCaseRepository workflowCases;
  Long reportId;
  Long seasonId;
  TransactionTemplate tx;

  @BeforeEach
  void setup() {
    tx = new TransactionTemplate(transactionManager);
    tx.executeWithoutResult(status -> {
      Workspace household = Workspace.builder().code("H-" + UUID.randomUUID()).type("HOUSEHOLD")
          .name("Test farmer").build();
      em.persist(household);
      Parcel parcel = Parcel.builder().code("P-" + UUID.randomUUID()).householdId(household.getId())
          .farmerName("Test farmer").areaHectares(BigDecimal.ONE)
          .approvalStatus(ApprovalStatus.APPROVED).build();
      em.persist(parcel);
      CropSeason season = CropSeason.builder().parcel(parcel).riceVariety("OM 5451")
          .startDate(LocalDate.now().minusDays(30)).approvalStatus(ApprovalStatus.APPROVED).build();
      em.persist(season);
      DiseaseReport report = DiseaseReport.builder().cropSeason(season).imageRef("test.png")
          .reportedBy("Test farmer").reportedByUserId("farmer-test").build();
      em.persist(report);
      Disease disease = Disease.builder().code("Rice___Blast").name("Blast").build();
      if (em.createQuery("select d from Disease d where d.code='Rice___Blast'", Disease.class)
          .getResultList().isEmpty())
        em.persist(disease);
      em.flush();
      reportId = report.getId();
      seasonId = season.getId();
    });
    when(user.canAccess(any())).thenReturn(true);
    when(user.currentUserId()).thenReturn("engineer-test");
    when(user.currentFullName()).thenReturn("Test engineer");
  }

  private AiDiagnoseResponse response() {
    return new AiDiagnoseResponse("test",
        new AiDiagnoseResponse.CvResult("Rice___Blast", "Blast", new BigDecimal("0.91"), "YOLO",
            "test"),
        List.of(), new AiDiagnoseResponse.StructuredContext("review_required", List.of()),
        new AiDiagnoseResponse.Recommendation(null, true));
  }

  @Test
  void seededBrownSpotRuleRequiresApprovalAndLocksHarvestForFourteenDays() throws Exception {
    try (var connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(connection, new ClassPathResource("farm/data.sql"));
    }
    var facts = context.getDiagnosisContext("brown-spot-test", "Rice___Brown_spot", seasonId);
    assertThat(facts.rules()).hasSize(1);
    var rule = facts.rules().getFirst();
    assertThat(rule.pesticide()).isEqualTo("Help 400SC");
    assertThat(rule.doseMin()).isEqualByComparingTo("0.6");
    assertThat(rule.doseMax()).isEqualByComparingTo("0.6");
    assertThat(rule.phiDays()).isEqualTo(14);
    var aiRule = new AiDiagnoseResponse.RuleFact(rule.ruleId(), rule.pesticide(), rule.doseMin(),
        rule.doseMax(), rule.doseUnit(), rule.phiDays(), rule.version());
    when(ai.diagnose(any())).thenReturn(new AiDiagnoseResponse("brown-spot-test",
        new AiDiagnoseResponse.CvResult("Rice___Brown_spot", "Brown Spot", new BigDecimal("0.9"),
            "test-fixture", "test"),
        List.of(), new AiDiagnoseResponse.StructuredContext("treatment", List.of(aiRule)),
        new AiDiagnoseResponse.Recommendation(rule.ruleId(), true)));

    var diagnosis = diagnoses.createFromAi(reportId);
    assertThat(diagnosis.approvalStatus()).isEqualTo("PENDING");
    assertThat(diagnosis.treatmentRule().pesticideCode()).isEqualTo("HELP_400SC");
    assertThat(diagnosis.treatmentRule().notes()).contains("400 L/ha", "khi bệnh chớm xuất hiện");
    assertThatThrownBy(() -> diagnoses.selfReport(reportId,
        new DiagnosisApplyRequest(new BigDecimal("0.6"), null)))
        .isInstanceOf(InvalidStateException.class);

    diagnoses.approve(reportId);
    assertThatThrownBy(() -> diagnoses.selfReport(reportId,
        new DiagnosisApplyRequest(new BigDecimal("0.61"), null)))
        .isInstanceOf(InvalidStateException.class);
    LocalDate appliedAt = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
    var application =
        diagnoses.selfReport(reportId, new DiagnosisApplyRequest(new BigDecimal("0.6"), appliedAt));
    assertThat(application.phiDaysSnapshot()).isEqualTo(14);
    assertThat(application.harvestLockedUntil()).isEqualTo(appliedAt.plusDays(14));
    diagnoses.confirmApplication(reportId);
    var harvest = new HarvestRequest(null, new BigDecimal("1000"), null, null, null, "test");
    assertThatThrownBy(() -> seasons.harvest(seasonId, harvest)).isInstanceOfSatisfying(
        PokaYokeViolationException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo("POKA_YOKE_VIETGAP_VIOLATION"));

    try (var connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(connection, new ClassPathResource("farm/data.sql"));
    }
    assertThat(diagnoses.getApplication(reportId).harvestLockedUntil())
        .isEqualTo(application.harvestLockedUntil());
    assertThat(diagnoses.getApplication(reportId).phiDaysSnapshot()).isEqualTo(14);
  }

  @Test
  void rerunPreservesHistoryAndUsesLatestDiagnosis() {
    when(ai.diagnose(any())).thenReturn(response());
    var first = diagnoses.createFromAi(reportId);
    var second = diagnoses.createFromAi(reportId);
    assertThat(second.id()).isGreaterThan(first.id());
    assertThat(diagnoses.history(reportId)).extracting(DiagnosisResponse::id)
        .containsExactly(second.id(), first.id());
    assertThat(reports.getById(reportId).diagnosis().id()).isEqualTo(second.id());
    assertThat(reports.listByCropSeason(seasonId, false).getFirst().diagnosis().id())
        .isEqualTo(second.id());
  }

  @Test
  void rerunClosesPreviousPendingWorkflowAndApprovalUsesLatestCase() {
    when(ai.diagnose(any())).thenReturn(response());
    var first = diagnoses.createFromAi(reportId);
    var second = diagnoses.createFromAi(reportId);
    assertThat(workflowCases
        .findByCaseTypeAndEntityTypeAndEntityId("DIAGNOSIS_APPROVAL", "DIAGNOSIS", first.id())
        .orElseThrow().getStatus()).isEqualTo(WorkflowStatus.CANCELLED);
    assertThat(diagnosisRepository.findById(first.id()).orElseThrow().getApprovalStatus())
        .isEqualTo(ApprovalStatus.PENDING);
    diagnoses.approve(reportId);
    assertThat(workflowCases
        .findByCaseTypeAndEntityTypeAndEntityId("DIAGNOSIS_APPROVAL", "DIAGNOSIS", second.id())
        .orElseThrow().getStatus()).isEqualTo(WorkflowStatus.APPROVED);
  }

  @Test
  void reportResponseAndAiRequestUseTheirOwnPresignedUrls() {
    when(objectStorage.presignedUrl("test.png")).thenReturn("https://public.test/test.png");
    when(objectStorage.internalPresignedUrl(eq("test.png"), any()))
        .thenReturn("http://seaweedfs:8333/reports/test.png");
    assertThat(reports.getById(reportId).imageUrl()).isEqualTo("https://public.test/test.png");
    when(ai.diagnose(any())).thenAnswer(call -> {
      AiDiagnoseRequest request = call.getArgument(0);
      assertThat(request.imageUrl()).isEqualTo("http://seaweedfs:8333/reports/test.png");
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return response();
    });
    diagnoses.createFromAi(reportId);
  }

  @Test
  void aiWaitHasNoTransactionOrBorrowedDatabaseConnection() {
    when(ai.diagnose(any())).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
      try (var connection = dataSource.getConnection()) {
        assertThat(connection.isClosed()).isFalse();
      }
      return response();
    });
    diagnoses.createFromAi(reportId);
  }

  @Test
  void manualDiagnosisWithoutRuleStoresStructuredEngineerLabel() {
    var result = diagnoses.create(reportId, new DiagnosisCreateRequest("Rice___Leaf_smut",
        new BigDecimal("0.8"), "engineer", "Vết hình thoi trên lá"));
    assertThat(result.treatmentRule()).isNull();
    assertThat(result.recommendationText()).contains("Chờ khuyến nghị");
    assertThat(labelRepository.findAll())
        .noneMatch(l -> l.getDiagnosis().getId().equals(result.id()));
    diagnoses.approve(reportId);
    tx.executeWithoutResult(status -> {
      var label = labelRepository.findAll().stream()
          .filter(l -> l.getDiagnosis().getId().equals(result.id())).findFirst().orElseThrow();
      assertThat(label.getSymptomText()).isEqualTo("Vết hình thoi trên lá");
      assertThat(label.getEngineerUserId()).isEqualTo("engineer-test");
    });
  }

  @Test
  void diagnosisRecordsWhoProducedItAndOnlyAiOnesReachTheOutcomeView() {
    when(ai.diagnose(any())).thenReturn(response());
    var fromAi = diagnoses.createFromAi(reportId);
    var typed = diagnoses.create(reportId, new DiagnosisCreateRequest("Rice___Leaf_smut",
        new BigDecimal("0.8"), "engineer", "Vết hình thoi trên lá"));
    diagnoses.approve(reportId);

    assertThat(diagnosisRepository.findById(fromAi.id()).orElseThrow().getSource())
        .isEqualTo(DiagnosisSource.AI);
    assertThat(diagnosisRepository.findById(typed.id()).orElseThrow().getSource())
        .isEqualTo(DiagnosisSource.ENGINEER);
    var inView = new JdbcTemplate(dataSource).queryForList(
        "SELECT diagnosis_id FROM vw_ai_diagnosis_outcomes WHERE diagnosis_id IN (?, ?)",
        Long.class, fromAi.id(), typed.id());
    assertThat(inView).doesNotContain(typed.id());
  }

  @Test
  void retakeChangesReportAndPreservesDiagnosisDecision() {
    when(ai.diagnose(any())).thenReturn(response());
    var diagnosis = diagnoses.createFromAi(reportId);
    var report = reports.requestRetake(reportId, "Ảnh thiếu chi tiết");
    assertThat(report.status()).isEqualTo("NEEDS_RETAKE");
    assertThat(report.diagnosis().approvalStatus()).isEqualTo(diagnosis.approvalStatus());
    assertThatThrownBy(() -> diagnoses.createFromAi(reportId))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void serverRejectsBlurEvenWhenClientSkipsPrecheck() {
    when(storage.sanitize(any())).thenReturn(new byte[] {1});
    when(ai.precheck(any())).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return new ImagePrecheckResponse(false, List.of("Ảnh mờ"), 256, 256, 0, 100);
    });
    long before = reportRepository.count();
    assertThatThrownBy(() -> reports.create(seasonId,
        new MockMultipartFile("image", new byte[] {1}), UUID.randomUUID()))
        .isInstanceOf(AiTriageException.class);
    assertThat(reportRepository.count()).isEqualTo(before);
    verify(storage, never()).store(any(), any());
  }

  @Test
  void serverRejectsNonRiceEvenWhenClientSkipsPrecheck() {
    when(storage.sanitize(any())).thenReturn(new byte[] {1});
    when(ai.precheck(any())).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return new ImagePrecheckResponse(false, List.of("Ảnh chưa được nhận diện là lúa"), 640, 480,
          100, 100);
    });
    long before = reportRepository.count();
    assertThatThrownBy(() -> reports.create(seasonId,
        new MockMultipartFile("image", new byte[] {1}), UUID.randomUUID()))
        .isInstanceOf(AiTriageException.class);
    assertThat(reportRepository.count()).isEqualTo(before);
    verify(storage, never()).store(any(), any());
  }

  @Test
  void unapprovedSeasonRejectsReportBeforeCallingPrecheck() {
    for (ApprovalStatus approval : List.of(ApprovalStatus.PENDING, ApprovalStatus.REJECTED)) {
      tx.executeWithoutResult(
          status -> em.find(CropSeason.class, seasonId).setApprovalStatus(approval));
      assertThatThrownBy(() -> reports.create(seasonId,
          new MockMultipartFile("image", new byte[] {1}), UUID.randomUUID()))
          .isInstanceOfSatisfying(PokaYokeViolationException.class,
              error -> assertThat(error.getErrorCode()).isEqualTo("CROP_SEASON_NOT_APPROVED"));
    }
    verify(ai, never()).precheck(any());
    verify(storage, never()).store(any(), any());
  }

  @Test
  void linkedRetakeAndIdempotentReplayStayInSameChain() {
    reports.requestRetake(reportId, "Chụp gần hơn");
    when(user.currentUserId()).thenReturn("farmer-test");
    when(storage.sanitize(any())).thenReturn(new byte[] {1});
    when(storage.store(any(), eq("farmer-test"))).thenReturn("retake.png");
    when(ai.precheck(any()))
        .thenReturn(new ImagePrecheckResponse(true, List.of(), 256, 256, 100, 100));
    UUID key = UUID.randomUUID();
    var image = new MockMultipartFile("image", new byte[] {1});
    var replacement = reports.create(seasonId, image, key, reportId);
    assertThat(replacement.retakeOf()).isEqualTo(reportId);
    assertThat(reports.getById(reportId).retakeIds()).containsExactly(replacement.id());
    assertThat(reports.create(seasonId, image, key, reportId).id()).isEqualTo(replacement.id());
    verify(ai, times(1)).precheck(any());
    assertThatThrownBy(() -> reports.create(seasonId, image, UUID.randomUUID(), reportId))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void lateAiResultCannotReplaceANewerEngineerDiagnosis() {
    when(ai.diagnose(any())).thenAnswer(call -> {
      diagnoses.create(reportId,
          new DiagnosisCreateRequest("Rice___Blast", new BigDecimal("0.8"), "engineer"));
      return response();
    });
    assertThatThrownBy(() -> diagnoses.createFromAi(reportId))
        .isInstanceOf(InvalidStateException.class);
    assertThat(diagnoses.history(reportId)).hasSize(1);
  }

  @Test
  void olderTreatmentStillLocksHarvestAfterDiagnosisRerun() {
    when(ai.diagnose(any())).thenReturn(response());
    var original = diagnoses.createFromAi(reportId);
    tx.executeWithoutResult(status -> {
      Pesticide pesticide = Pesticide.builder().code("X-" + UUID.randomUUID())
          .name("Test pesticide").unit("L").build();
      em.persist(pesticide);
      em.persist(TreatmentApplication.builder().cropSeason(em.find(CropSeason.class, seasonId))
          .diagnosis(em.find(Diagnosis.class, original.id())).pesticide(pesticide)
          .doseAmountSnapshot(BigDecimal.ONE).doseUnitSnapshot("L/ha").phiDaysSnapshot(14)
          .appliedAt(LocalDate.now()).harvestLockedUntil(LocalDate.now().plusDays(14))
          .confirmationStatus(TreatmentConfirmationStatus.CONFIRMED).build());
    });
    diagnoses.createFromAi(reportId);
    var harvest = new HarvestRequest(null, new BigDecimal("1000"), null, null, null, "test");
    assertThatThrownBy(() -> seasons.harvest(seasonId, harvest)).isInstanceOfSatisfying(
        PokaYokeViolationException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo("POKA_YOKE_VIETGAP_VIOLATION"));
  }

}
