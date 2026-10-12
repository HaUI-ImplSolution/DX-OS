// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.config.TimeConfig;
import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.core.identity.HouseholdWorkspaceDirectory;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowActor;
import HaUI.ImplSolution.dxos.core.workflow.WorkflowService;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.farm.model.ApprovalStatus;
import HaUI.ImplSolution.dxos.farm.model.CropSeason;
import HaUI.ImplSolution.dxos.farm.model.CropSeasonStatus;
import HaUI.ImplSolution.dxos.farm.model.Diagnosis;
import HaUI.ImplSolution.dxos.core.identity.Household;
import HaUI.ImplSolution.dxos.farm.model.DiseaseReport;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import HaUI.ImplSolution.dxos.farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.dxos.farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.dxos.farm.repository.DiseaseReportRepository;
import HaUI.ImplSolution.dxos.farm.repository.DiseaseRepository;
import HaUI.ImplSolution.dxos.core.identity.HouseholdRepository;
import HaUI.ImplSolution.dxos.farm.repository.ParcelRepository;
import HaUI.ImplSolution.dxos.farm.service.AiDiagnosisClient;
import HaUI.ImplSolution.dxos.farm.service.CurrentUser;
import HaUI.ImplSolution.dxos.farm.service.DiagnosisService;
import HaUI.ImplSolution.dxos.farm.service.NotificationService;
import HaUI.ImplSolution.dxos.farm.service.TreatmentRuleService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.RollbackException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Runs against a real PostgreSQL because it checks the database-side guarantees: the @Version
// columns and the partial unique index created by the Flyway migrations. Hibernate validates the
// entities against that schema when the context starts.
@DataJpaTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({DiagnosisService.class, WorkflowService.class, NotificationService.class,
    HouseholdWorkspaceDirectory.class, TimeConfig.class, PersistenceTestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConcurrencyIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    MigratedDatabase.migrate(POSTGRES);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @MockitoBean
  private AiDiagnosisClient aiDiagnosisClient;
  @MockitoBean
  private TreatmentRuleService treatmentRuleService;
  @MockitoBean
  private CurrentUser currentUserService;
  @MockitoBean
  private ObjectStorage storageService;

  @Autowired
  private DiagnosisService diagnosisService;
  @Autowired
  private WorkflowService workflowService;
  @Autowired
  private EntityManagerFactory entityManagerFactory;
  @Autowired
  private ParcelRepository parcelRepository;
  @Autowired
  private HouseholdRepository householdRepository;
  @Autowired
  private CropSeasonRepository cropSeasonRepository;
  @Autowired
  private DiseaseReportRepository diseaseReportRepository;
  @Autowired
  private DiseaseRepository diseaseRepository;
  @Autowired
  private DiagnosisRepository diagnosisRepository;

  private Parcel newParcel() {
    String code = "P-" + UUID.randomUUID();
    Household household = householdRepository
        .save(Household.builder().code("T-" + UUID.randomUUID()).fullName("Test Farmer").build());
    return parcelRepository
        .save(Parcel.builder().code(code).householdId(household.getId()).farmerName("Test Farmer")
            .areaHectares(BigDecimal.ONE).approvalStatus(ApprovalStatus.APPROVED).build());
  }

  private CropSeason newSeason(Parcel parcel, CropSeasonStatus status) {
    return cropSeasonRepository.saveAndFlush(CropSeason.builder().parcel(parcel)
        .riceVariety("OM 5451").startDate(LocalDate.of(2026, 7, 1)).status(status)
        .approvalStatus(ApprovalStatus.APPROVED).build());
  }

  private Diagnosis newPendingDiagnosis() {
    CropSeason season = newSeason(newParcel(), CropSeasonStatus.ACTIVE);
    DiseaseReport report = diseaseReportRepository.save(DiseaseReport.builder().cropSeason(season)
        .imageRef("x.jpg").reportedBy("Test Farmer").build());
    Diagnosis diagnosis = diagnosisRepository.save(Diagnosis.builder().diseaseReport(report)
        .disease(diseaseRepository.findByCodeAndActiveTrue("Rice___Blast").orElseThrow())
        .confidence(new BigDecimal("0.9000")).modelVersion("test").recommendationText("test")
        .approvalStatus(ApprovalStatus.PENDING).build());
    workflowService.createCase("DIAGNOSIS_APPROVAL", "DIAGNOSIS", diagnosis.getId(),
        new WorkflowActor("eng-1", "Engineer"));
    return diagnosis;
  }

  @Test
  void secondSeasonActiveOnSameParcelIsRejectedByDatabase() {
    Parcel parcel = newParcel();
    newSeason(parcel, CropSeasonStatus.ACTIVE);

    assertThatThrownBy(() -> newSeason(parcel, CropSeasonStatus.ACTIVE))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void harvestedSeasonsDoNotCountAgainstTheActiveLimit() {
    Parcel parcel = newParcel();
    newSeason(parcel, CropSeasonStatus.HARVESTED);
    newSeason(parcel, CropSeasonStatus.HARVESTED);

    assertThat(newSeason(parcel, CropSeasonStatus.ACTIVE).getId()).isNotNull();
  }

  @Test
  void staleDiagnosisWriterFailsOnCommit() {
    Diagnosis diagnosis = newPendingDiagnosis();
    EntityManager first = entityManagerFactory.createEntityManager();
    EntityManager second = entityManagerFactory.createEntityManager();
    try {
      first.getTransaction().begin();
      second.getTransaction().begin();
      Diagnosis a = first.find(Diagnosis.class, diagnosis.getId());
      Diagnosis b = second.find(Diagnosis.class, diagnosis.getId());
      a.setApprovalStatus(ApprovalStatus.APPROVED);
      first.getTransaction().commit();
      b.setApprovalStatus(ApprovalStatus.REJECTED);

      assertThatThrownBy(() -> second.getTransaction().commit())
          .isInstanceOf(RollbackException.class);
    } finally {
      first.close();
      second.close();
    }
    assertThat(diagnosisRepository.findById(diagnosis.getId()).orElseThrow().getApprovalStatus())
        .isEqualTo(ApprovalStatus.APPROVED);
  }

  @Test
  void staleCropSeasonWriterFailsOnCommit() {
    CropSeason season = newSeason(newParcel(), CropSeasonStatus.ACTIVE);
    EntityManager first = entityManagerFactory.createEntityManager();
    EntityManager second = entityManagerFactory.createEntityManager();
    try {
      first.getTransaction().begin();
      second.getTransaction().begin();
      CropSeason a = first.find(CropSeason.class, season.getId());
      CropSeason b = second.find(CropSeason.class, season.getId());
      a.setStatus(CropSeasonStatus.HARVESTED);
      first.getTransaction().commit();
      b.setRiceVariety("Other");

      assertThatThrownBy(() -> second.getTransaction().commit())
          .isInstanceOf(RollbackException.class);
    } finally {
      first.close();
      second.close();
    }
  }

  @Test
  void concurrentApprovalsLetExactlyOneRequestWin() throws Exception {
    Diagnosis diagnosis = newPendingDiagnosis();
    Long reportId = diagnosis.getDiseaseReport().getId();
    when(currentUserService.currentFullName()).thenReturn("Engineer");
    when(currentUserService.currentUserId()).thenReturn("engineer-test");
    int callers = 2;
    CyclicBarrier start = new CyclicBarrier(callers);
    ExecutorService executor = Executors.newFixedThreadPool(callers);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < callers; i++) {
        Callable<Boolean> approve = () -> {
          start.await();
          try {
            diagnosisService.approve(reportId);
            return true;
          } catch (ObjectOptimisticLockingFailureException | InvalidStateException e) {
            // Both exceptions are mapped to HTTP 409 by GlobalExceptionHandler.
            return false;
          }
        };
        results.add(executor.submit(approve));
      }
      long winners = 0;
      for (Future<Boolean> result : results) {
        if (result.get()) {
          winners++;
        }
      }
      assertThat(winners).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
    assertThat(diagnosisRepository.findById(diagnosis.getId()).orElseThrow().getApprovalStatus())
        .isEqualTo(ApprovalStatus.APPROVED);
  }

  @Test
  void concurrentAiRerunsKeepOnlyOneNewCurrentDiagnosis() throws Exception {
    Diagnosis original = newPendingDiagnosis();
    Long reportId = original.getDiseaseReport().getId();
    CyclicBarrier aiCalls = new CyclicBarrier(2);
    org.mockito.Mockito.when(aiDiagnosisClient.diagnose(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(call -> {
          aiCalls.await(20, java.util.concurrent.TimeUnit.SECONDS);
          return new HaUI.ImplSolution.dxos.farm.dto.AiDiagnoseResponse("concurrent-test",
              new HaUI.ImplSolution.dxos.farm.dto.AiDiagnoseResponse.CvResult(
                  original.getDisease().getCode(), original.getDisease().getName(),
                  new BigDecimal("0.91"), "YOLO", "test"),
              List.of(),
              new HaUI.ImplSolution.dxos.farm.dto.AiDiagnoseResponse.StructuredContext(
                  "review_required", List.of()),
              new HaUI.ImplSolution.dxos.farm.dto.AiDiagnoseResponse.Recommendation(null,
                  "Review required", true));
        });
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> rerun = () -> {
        try {
          diagnosisService.createFromAi(reportId);
          return true;
        } catch (InvalidStateException | ObjectOptimisticLockingFailureException
            | jakarta.persistence.OptimisticLockException e) {
          return false;
        }
      };
      Future<Boolean> first = executor.submit(rerun);
      Future<Boolean> second = executor.submit(rerun);
      assertThat((first.get() ? 1 : 0) + (second.get() ? 1 : 0)).isEqualTo(1);
      assertThat(diagnosisService.history(reportId)).hasSize(2);
    } finally {
      executor.shutdownNow();
    }
  }

}
