// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceDirectory;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceMemberRole;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;
import HaUI.ImplSolution.dxos.core.config.TimeConfig;
import HaUI.ImplSolution.dxos.core.identity.Workspace;
import HaUI.ImplSolution.dxos.core.identity.WorkspaceDirectoryService;
import HaUI.ImplSolution.dxos.core.identity.WorkspaceMember;
import HaUI.ImplSolution.dxos.core.notification.NotificationInboxService;
import HaUI.ImplSolution.dxos.core.notification.NotificationResponse;
import HaUI.ImplSolution.dxos.core.notification.NotificationTypes;
import HaUI.ImplSolution.dxos.core.workflow.PendingRequestLimiter;
import HaUI.ImplSolution.dxos.core.workflow.WorkflowDefinitions;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowActor;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowStatus;
import HaUI.ImplSolution.dxos.core.workflow.WorkflowCaseRepository;
import HaUI.ImplSolution.dxos.core.workflow.WorkflowService;
import HaUI.ImplSolution.dxos.farm.FarmAppModule;
import HaUI.ImplSolution.dxos.farm.FarmNotificationConfig;
import HaUI.ImplSolution.dxos.farm.FarmWorkflowConfig;
import HaUI.ImplSolution.dxos.farm.dto.CropSeasonCreateRequest;
import HaUI.ImplSolution.dxos.farm.dto.ParcelCreateRequest;
import HaUI.ImplSolution.dxos.farm.dto.ParcelResponse;
import HaUI.ImplSolution.dxos.farm.model.ApprovalStatus;
import HaUI.ImplSolution.dxos.farm.model.CropSeason;
import HaUI.ImplSolution.dxos.farm.model.CropSeasonStatus;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import HaUI.ImplSolution.dxos.farm.model.SeasonType;
import HaUI.ImplSolution.dxos.farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.dxos.farm.repository.ParcelRepository;
import HaUI.ImplSolution.dxos.farm.service.CropSeasonApprovalHandler;
import HaUI.ImplSolution.dxos.farm.service.CropSeasonService;
import HaUI.ImplSolution.dxos.farm.service.CurrentUser;
import HaUI.ImplSolution.dxos.farm.service.GpsFixValidator;
import HaUI.ImplSolution.dxos.farm.service.HarvestReminderService;
import HaUI.ImplSolution.dxos.farm.service.NotificationService;
import HaUI.ImplSolution.dxos.farm.service.ParcelApprovalHandler;
import HaUI.ImplSolution.dxos.farm.service.ParcelCodeGenerator;
import HaUI.ImplSolution.dxos.farm.service.ParcelService;
import HaUI.ImplSolution.dxos.farm.service.SeasonCalendar;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Decisions on parcels and seasons reach the household as notifications, and the daily harvest
// reminder is sent once per season and day.
@DataJpaTest(properties = {"spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({ParcelService.class, CropSeasonService.class, CurrentUser.class, ParcelCodeGenerator.class,
    GpsFixValidator.class, SeasonCalendar.class, PendingRequestLimiter.class, FarmAppModule.class,
    WorkflowService.class, WorkflowDefinitions.class, FarmWorkflowConfig.class,
    NotificationService.class, NotificationInboxService.class, NotificationTypes.class,
    FarmNotificationConfig.class, WorkspaceDirectoryService.class, TimeConfig.class,
    ParcelApprovalHandler.class, CropSeasonApprovalHandler.class, HarvestReminderService.class,
    PersistenceTestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FarmNotificationIntegrationTest {

  private static final WorkflowActor ADMIN = new WorkflowActor("admin-1", "Administrator");

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
  ParcelService parcels;
  @Autowired
  CropSeasonService seasons;
  @Autowired
  ParcelRepository parcelRepository;
  @Autowired
  CropSeasonRepository seasonRepository;
  @Autowired
  WorkflowService workflow;
  @Autowired
  WorkflowCaseRepository workflowCases;
  @Autowired
  HarvestReminderService reminders;
  @Autowired
  NotificationInboxService inbox;
  @Autowired
  WorkspaceDirectory directory;
  @Autowired
  EntityManager em;
  @Autowired
  PlatformTransactionManager transactionManager;
  @Autowired
  Clock clock;
  @MockitoBean
  UserContext userContext;

  TransactionTemplate tx;
  LocalDate today;

  @BeforeEach
  void setup() {
    tx = new TransactionTemplate(transactionManager);
    today = LocalDate.now(clock);
  }

  private WorkspaceView household() {
    String code = "H-" + UUID.randomUUID().toString().substring(0, 8);
    String owner = "owner-" + UUID.randomUUID();
    tx.executeWithoutResult(status -> {
      Workspace workspace =
          Workspace.builder().code(code).type("HOUSEHOLD").name("Household " + code).build();
      em.persist(workspace);
      em.persist(WorkspaceMember.builder().workspaceId(workspace.getId()).userId(owner)
          .role(WorkspaceMemberRole.OWNER).build());
    });
    return directory.findByCode(code).orElseThrow();
  }

  private Parcel approvedParcel(WorkspaceView household) {
    return tx.execute(status -> {
      Parcel parcel = Parcel.builder().code("P-" + UUID.randomUUID()).householdId(household.id())
          .farmerName(household.name()).areaHectares(BigDecimal.ONE)
          .approvalStatus(ApprovalStatus.APPROVED).build();
      em.persist(parcel);
      return parcel;
    });
  }

  private CropSeason season(Parcel parcel, LocalDate expectedHarvest, CropSeasonStatus status) {
    return tx.execute(state -> {
      CropSeason season = CropSeason.builder().parcel(parcel).riceVariety("OM 5451")
          .riceVarietyCode("OM5451").seasonType(SeasonType.DONG_XUAN).seasonYear(today.getYear())
          .code("S-" + UUID.randomUUID()).startDate(today.minusDays(60))
          .expectedHarvestDate(expectedHarvest).approvalStatus(ApprovalStatus.APPROVED)
          .status(status).build();
      em.persist(season);
      return season;
    });
  }

  private void asFarmer(WorkspaceView household) {
    reset(userContext);
    when(userContext.currentUserId()).thenReturn(household.ownerUserId());
    when(userContext.currentFullName()).thenReturn("Farmer " + household.code());
    when(userContext.currentWorkspace()).thenReturn(Optional.of(household));
    when(userContext.roles()).thenReturn(Set.of("FARMER"));
    when(userContext.hasRole("FARMER")).thenReturn(true);
    when(userContext.isStaff()).thenReturn(false);
  }

  private List<NotificationResponse> messagesOf(WorkspaceView household) {
    asFarmer(household);
    return inbox.listMine(false, 0, 50).items();
  }

  private void decide(String caseType, String entityType, long entityId, String target,
      String reason) {
    Long caseId =
        workflowCases.findByCaseTypeAndEntityTypeAndEntityId(caseType, entityType, entityId)
            .orElseThrow().getId();
    workflow.decide(caseId, target, ADMIN, reason);
  }

  @Test
  void approvedParcelNotifiesTheHousehold() {
    WorkspaceView household = household();
    asFarmer(household);
    ParcelResponse created = parcels.create(new ParcelCreateRequest(null, BigDecimal.ONE, "Field",
        new BigDecimal("10.0"), new BigDecimal("105.0"), new BigDecimal("5"), false),
        UUID.randomUUID());

    decide("PARCEL_APPROVAL", "PARCEL", created.id(), WorkflowStatus.APPROVED, null);

    assertThat(parcelRepository.findById(created.id()).orElseThrow().getApprovalStatus())
        .isEqualTo(ApprovalStatus.APPROVED);
    var messages = messagesOf(household);
    assertThat(messages).extracting(NotificationResponse::type).containsExactly("PARCEL_APPROVED");
    assertThat(messages.getFirst().relatedEntityRef()).isEqualTo(created.code());
  }

  @Test
  void rejectedParcelCarriesTheReasonToTheHousehold() {
    WorkspaceView household = household();
    asFarmer(household);
    ParcelResponse created = parcels.create(new ParcelCreateRequest(null, BigDecimal.ONE, "Field",
        new BigDecimal("10.0"), new BigDecimal("105.0"), new BigDecimal("5"), false),
        UUID.randomUUID());

    decide("PARCEL_APPROVAL", "PARCEL", created.id(), WorkflowStatus.REJECTED,
        "Vị trí nằm ngoài hợp tác xã");

    Parcel parcel = parcelRepository.findById(created.id()).orElseThrow();
    assertThat(parcel.getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
    assertThat(parcel.getRejectionReason()).isEqualTo("Vị trí nằm ngoài hợp tác xã");
    var messages = messagesOf(household);
    assertThat(messages).extracting(NotificationResponse::type).containsExactly("PARCEL_REJECTED");
    assertThat(messages.getFirst().body()).contains("Vị trí nằm ngoài hợp tác xã");
  }

  @Test
  void approvedSeasonNotifiesTheHousehold() {
    WorkspaceView household = household();
    Parcel parcel = approvedParcel(household);
    asFarmer(household);
    var created = seasons.create(parcel.getCode(),
        new CropSeasonCreateRequest("OM5451", today, SeasonType.HE_THU, null, null),
        UUID.randomUUID());

    decide("CROP_SEASON_APPROVAL", "CROP_SEASON", created.id(), WorkflowStatus.APPROVED, null);

    assertThat(seasonRepository.findById(created.id()).orElseThrow().getApprovalStatus())
        .isEqualTo(ApprovalStatus.APPROVED);
    assertThat(messagesOf(household)).extracting(NotificationResponse::type)
        .containsExactly("CROP_SEASON_APPROVED");
  }

  @Test
  void dueSeasonIsRemindedOncePerDay() {
    WorkspaceView household = household();
    CropSeason due = season(approvedParcel(household), today.minusDays(1), CropSeasonStatus.ACTIVE);

    assertThat(reminders.sendDueReminders()).isGreaterThanOrEqualTo(1);
    assertThat(reminders.sendDueReminders()).isZero();

    var messages = messagesOf(household);
    assertThat(messages).extracting(NotificationResponse::type).containsExactly("HARVEST_REMINDER");
    assertThat(messages.getFirst().relatedEntityRef()).isEqualTo(String.valueOf(due.getId()));
  }

  @Test
  void seasonsNotYetDueOrAlreadyHarvestedGetNoReminder() {
    WorkspaceView household = household();
    Parcel parcel = approvedParcel(household);
    season(parcel, today.plusDays(5), CropSeasonStatus.ACTIVE);
    season(approvedParcel(household), today.minusDays(3), CropSeasonStatus.HARVESTED);

    reminders.sendDueReminders();

    assertThat(messagesOf(household)).isEmpty();
  }
}
