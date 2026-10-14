// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceDirectory;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceMemberRole;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;
import HaUI.ImplSolution.dxos.core.config.TimeConfig;
import HaUI.ImplSolution.dxos.core.identity.Workspace;
import HaUI.ImplSolution.dxos.core.identity.WorkspaceDirectoryService;
import HaUI.ImplSolution.dxos.core.identity.WorkspaceMember;
import HaUI.ImplSolution.dxos.core.notification.NotificationInboxService;
import HaUI.ImplSolution.dxos.core.notification.NotificationTypes;
import HaUI.ImplSolution.dxos.core.workflow.PendingRequestLimiter;
import HaUI.ImplSolution.dxos.core.workflow.WorkflowDefinitions;
import HaUI.ImplSolution.dxos.core.workflow.WorkflowService;
import HaUI.ImplSolution.dxos.farm.FarmAppModule;
import HaUI.ImplSolution.dxos.farm.FarmNotificationConfig;
import HaUI.ImplSolution.dxos.farm.FarmWorkflowConfig;
import HaUI.ImplSolution.dxos.farm.dto.CropSeasonCreateRequest;
import HaUI.ImplSolution.dxos.farm.dto.CropSeasonResponse;
import HaUI.ImplSolution.dxos.farm.dto.HarvestRequest;
import HaUI.ImplSolution.dxos.farm.dto.HarvestRescheduleRequest;
import HaUI.ImplSolution.dxos.farm.dto.ParcelCreateRequest;
import HaUI.ImplSolution.dxos.farm.dto.ParcelResponse;
import HaUI.ImplSolution.dxos.farm.model.ApprovalStatus;
import HaUI.ImplSolution.dxos.farm.model.CropSeason;
import HaUI.ImplSolution.dxos.farm.model.CropSeasonStatus;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import HaUI.ImplSolution.dxos.farm.model.SeasonType;
import HaUI.ImplSolution.dxos.farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.dxos.farm.repository.ParcelRepository;
import HaUI.ImplSolution.dxos.farm.service.CropSeasonService;
import HaUI.ImplSolution.dxos.farm.service.CurrentUser;
import HaUI.ImplSolution.dxos.farm.service.GpsFixValidator;
import HaUI.ImplSolution.dxos.farm.service.NotificationService;
import HaUI.ImplSolution.dxos.farm.service.ParcelCodeGenerator;
import HaUI.ImplSolution.dxos.farm.service.ParcelService;
import HaUI.ImplSolution.dxos.farm.service.SeasonCalendar;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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

// Household scope of parcels and seasons, idempotent creation, and the harvest lifecycle with the
// notification the household receives, against the migrated schema.
@DataJpaTest(properties = {"spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({ParcelService.class, CropSeasonService.class, CurrentUser.class, ParcelCodeGenerator.class,
    GpsFixValidator.class, SeasonCalendar.class, PendingRequestLimiter.class, FarmAppModule.class,
    WorkflowService.class, WorkflowDefinitions.class, FarmWorkflowConfig.class,
    NotificationService.class, NotificationInboxService.class, NotificationTypes.class,
    FarmNotificationConfig.class, WorkspaceDirectoryService.class, TimeConfig.class,
    PersistenceTestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FarmScopeAndSeasonIntegrationTest {

  private static final String VARIETY = "OM5451";

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

  private CropSeason approvedSeason(Parcel parcel, LocalDate expectedHarvest) {
    return tx.execute(status -> {
      CropSeason season = CropSeason.builder().parcel(parcel).riceVariety("OM 5451")
          .riceVarietyCode(VARIETY).seasonType(SeasonType.DONG_XUAN).seasonYear(today.getYear())
          .code("S-" + UUID.randomUUID()).startDate(today.minusDays(60))
          .expectedHarvestDate(expectedHarvest).approvalStatus(ApprovalStatus.APPROVED).build();
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

  private void asAdmin() {
    reset(userContext);
    when(userContext.currentUserId()).thenReturn("admin-" + UUID.randomUUID());
    when(userContext.currentFullName()).thenReturn("Administrator");
    when(userContext.currentWorkspace()).thenReturn(Optional.empty());
    when(userContext.roles()).thenReturn(Set.of("ADMIN"));
    when(userContext.hasRole("ADMIN")).thenReturn(true);
    when(userContext.isStaff()).thenReturn(true);
  }

  private static ParcelCreateRequest parcelRequest() {
    return new ParcelCreateRequest(null, BigDecimal.ONE, "Test field", new BigDecimal("10.0"),
        new BigDecimal("105.0"), new BigDecimal("5"), false);
  }

  private CropSeasonCreateRequest seasonRequest(SeasonType type) {
    return new CropSeasonCreateRequest(VARIETY, today, type, null, null);
  }

  private static HarvestRequest harvest(Boolean confirmEarly, String earlyReason, String onBehalf) {
    return new HarvestRequest(null, new BigDecimal("1000"), null, confirmEarly, earlyReason,
        onBehalf);
  }

  @Test
  void farmerOnlySeesAndOpensParcelsOfTheirOwnHousehold() {
    WorkspaceView first = household();
    WorkspaceView second = household();
    Parcel own = approvedParcel(first);
    Parcel foreign = approvedParcel(second);

    asFarmer(first);
    assertThat(parcels.listAll(false)).extracting(ParcelResponse::code).contains(own.getCode())
        .doesNotContain(foreign.getCode());
    assertThat(parcels.getByCode(own.getCode()).code()).isEqualTo(own.getCode());
    assertThatThrownBy(() -> parcels.getByCode(foreign.getCode()))
        .isInstanceOf(ResourceNotFoundException.class);

    asAdmin();
    assertThat(parcels.listAll(false)).extracting(ParcelResponse::code).contains(own.getCode(),
        foreign.getCode());
  }

  @Test
  void farmerCannotListOrCreateSeasonsOnAnotherHouseholdsParcel() {
    WorkspaceView first = household();
    WorkspaceView second = household();
    Parcel foreign = approvedParcel(second);

    asFarmer(first);
    assertThatThrownBy(() -> seasons.listByParcelCode(foreign.getCode(), false))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> seasons.create(foreign.getCode(), seasonRequest(SeasonType.DONG_XUAN),
        UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void parcelCreatedTwiceWithTheSameKeyIsOneParcelAndAnotherHouseholdIsRefused() {
    WorkspaceView first = household();
    WorkspaceView second = household();
    UUID key = UUID.randomUUID();

    asFarmer(first);
    ParcelResponse created = parcels.create(parcelRequest(), key);
    ParcelResponse replay = parcels.create(parcelRequest(), key);

    assertThat(replay.code()).isEqualTo(created.code());
    assertThat(parcelRepository.findByHouseholdId(first.id())).hasSize(1);
    assertThat(created.approvalStatus()).isEqualTo("PENDING");

    asFarmer(second);
    assertThatThrownBy(() -> parcels.create(parcelRequest(), key))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void seasonCreatedTwiceWithTheSameKeyIsOneSeasonAndAnotherParcelIsRefused() {
    WorkspaceView household = household();
    Parcel parcel = approvedParcel(household);
    Parcel other = approvedParcel(household);
    UUID key = UUID.randomUUID();

    asFarmer(household);
    CropSeasonResponse created =
        seasons.create(parcel.getCode(), seasonRequest(SeasonType.DONG_XUAN), key);
    CropSeasonResponse replay =
        seasons.create(parcel.getCode(), seasonRequest(SeasonType.DONG_XUAN), key);

    assertThat(replay.id()).isEqualTo(created.id());
    assertThat(seasonRepository.findByParcel_CodeOrderByIdAsc(parcel.getCode())).hasSize(1);
    assertThatThrownBy(
        () -> seasons.create(other.getCode(), seasonRequest(SeasonType.DONG_XUAN), key))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void unapprovedSeasonCannotBeHarvestedOrRescheduled() {
    WorkspaceView household = household();
    Parcel parcel = approvedParcel(household);
    asFarmer(household);
    CropSeasonResponse pending =
        seasons.create(parcel.getCode(), seasonRequest(SeasonType.HE_THU), UUID.randomUUID());

    assertThat(pending.approvalStatus()).isEqualTo("PENDING");
    assertThatThrownBy(() -> seasons.harvest(pending.id(), harvest(null, null, null)))
        .isInstanceOfSatisfying(PokaYokeViolationException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("CROP_SEASON_NOT_APPROVED"));
    assertThatThrownBy(() -> seasons.rescheduleHarvest(pending.id(),
        new HarvestRescheduleRequest(today.plusDays(5), "Mưa kéo dài")))
        .isInstanceOfSatisfying(PokaYokeViolationException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("CROP_SEASON_NOT_APPROVED"));
  }

  @Test
  void earlyHarvestNeedsConfirmationAndAHarvestedSeasonIsFinal() {
    WorkspaceView household = household();
    CropSeason season = approvedSeason(approvedParcel(household), today.plusDays(10));
    asFarmer(household);

    assertThatThrownBy(() -> seasons.harvest(season.getId(), harvest(null, null, null)))
        .isInstanceOfSatisfying(PokaYokeViolationException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("HARVEST_TOO_EARLY"));
    assertThat(seasonRepository.findById(season.getId()).orElseThrow().getStatus())
        .isEqualTo(CropSeasonStatus.ACTIVE);

    CropSeasonResponse done =
        seasons.harvest(season.getId(), harvest(true, "Lúa chín sớm do nắng", null));
    assertThat(done.status()).isEqualTo("HARVESTED");
    assertThat(done.actualHarvestDate()).isEqualTo(today);

    assertThatThrownBy(() -> seasons.harvest(season.getId(), harvest(true, "Lần hai", null)))
        .isInstanceOf(InvalidStateException.class);
    assertThatThrownBy(() -> seasons.rescheduleHarvest(season.getId(),
        new HarvestRescheduleRequest(today.plusDays(20), "Sau khi thu hoạch")))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void rescheduleKeepsEveryChangeAndRefusesOutOfRangeDates() {
    WorkspaceView household = household();
    CropSeason season = approvedSeason(approvedParcel(household), today.plusDays(10));
    asFarmer(household);

    CropSeasonResponse moved = seasons.rescheduleHarvest(season.getId(),
        new HarvestRescheduleRequest(today.plusDays(20), "Mưa kéo dài"));
    assertThat(moved.expectedHarvestDate()).isEqualTo(today.plusDays(20));
    var history = seasons.listReschedules(season.getId());
    assertThat(history).hasSize(1);
    assertThat(history.getFirst().previousDate()).isEqualTo(today.plusDays(10));
    assertThat(history.getFirst().newDate()).isEqualTo(today.plusDays(20));

    assertThatThrownBy(() -> seasons.rescheduleHarvest(season.getId(),
        new HarvestRescheduleRequest(today.plusDays(20), "Cùng ngày")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> seasons.rescheduleHarvest(season.getId(),
        new HarvestRescheduleRequest(today.minusDays(1), "Ngày đã qua")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> seasons.rescheduleHarvest(season.getId(),
        new HarvestRescheduleRequest(today.plusDays(120), "Quá xa")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(seasons.listReschedules(season.getId())).hasSize(1);
  }

  @Test
  void administratorHarvestOnBehalfNeedsAReasonAndNotifiesTheHousehold() {
    WorkspaceView household = household();
    CropSeason season = approvedSeason(approvedParcel(household), today.minusDays(1));

    asAdmin();
    assertThatThrownBy(() -> seasons.harvest(season.getId(), harvest(null, null, null)))
        .isInstanceOf(IllegalArgumentException.class);
    seasons.harvest(season.getId(), harvest(null, null, "Nông dân nhờ ghi hộ"));

    asFarmer(household);
    var messages = inbox.listMine(false, 0, 10).items();
    assertThat(messages).extracting(message -> message.type()).containsExactly("HARVEST_RECORDED");
    assertThat(messages.getFirst().body()).contains("Nông dân nhờ ghi hộ");
    assertThat(messages.getFirst().relatedEntityRef()).isEqualTo(String.valueOf(season.getId()));
    assertThat(inbox.unreadCount()).isEqualTo(1);
    inbox.markRead(messages.getFirst().id());
    assertThat(inbox.unreadCount()).isZero();
  }
}
