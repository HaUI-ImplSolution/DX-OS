package HaUI.ImplSolution.DX_Farm.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import HaUI.ImplSolution.DX_Farm.config.TimeConfig;
import HaUI.ImplSolution.DX_Farm.core.identity.Household;
import HaUI.ImplSolution.DX_Farm.core.storage.StorageService;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowService;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowCaseRepository;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowStatus;
import HaUI.ImplSolution.DX_Farm.dto.*;
import HaUI.ImplSolution.DX_Farm.exception.*;
import HaUI.ImplSolution.DX_Farm.model.*;
import HaUI.ImplSolution.DX_Farm.repository.*;
import HaUI.ImplSolution.DX_Farm.service.*;
import jakarta.persistence.EntityManager;
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
    CropSeasonService.class, SeasonCalendar.class, PendingRequestLimiter.class,
    WorkflowService.class, TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class Phase6IntegrationTest {
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
  @MockitoBean
  AiDiagnosisClient ai;
  @MockitoBean
  CurrentUserService user;
  @MockitoBean
  FileStorageService storage;
  @MockitoBean
  StorageService objectStorage;
  @Autowired
  WorkflowCaseRepository workflowCases;
  Long reportId;
  Long seasonId;
  TransactionTemplate tx;

  @BeforeEach
  void setup() {
    tx = new TransactionTemplate(transactionManager);
    tx.executeWithoutResult(status -> {
      Household household =
          Household.builder().code("H-" + UUID.randomUUID()).fullName("Test farmer").build();
      em.persist(household);
      Parcel parcel = Parcel.builder().code("P-" + UUID.randomUUID()).household(household)
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
        new AiDiagnoseResponse.Recommendation(null, "Cần kỹ sư đánh giá", true));
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
        .thenReturn("http://minio:9000/reports/test.png");
    assertThat(reports.getById(reportId).imageUrl()).isEqualTo("https://public.test/test.png");
    when(ai.diagnose(any())).thenAnswer(call -> {
      AiDiagnoseRequest request = call.getArgument(0);
      assertThat(request.imageUrl()).isEqualTo("http://minio:9000/reports/test.png");
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
    tx.executeWithoutResult(status -> {
      var label = labelRepository.findAll().stream()
          .filter(l -> l.getDiagnosis().getId().equals(result.id())).findFirst().orElseThrow();
      assertThat(label.getSymptomText()).isEqualTo("Vết hình thoi trên lá");
      assertThat(label.getEngineerUserId()).isEqualTo("engineer-test");
    });
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
          .appliedAt(LocalDate.now()).harvestLockedUntil(LocalDate.now().plusDays(14)).build());
    });
    diagnoses.createFromAi(reportId);
    assertThatThrownBy(() -> seasons.harvest(seasonId))
        .isInstanceOf(PokaYokeViolationException.class);
  }

}
