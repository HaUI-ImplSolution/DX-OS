package HaUI.ImplSolution.DX_Farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowService;
import HaUI.ImplSolution.DX_Farm.dto.CropSeasonCreateRequest;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import HaUI.ImplSolution.DX_Farm.exception.PokaYokeViolationException;
import HaUI.ImplSolution.DX_Farm.model.ApprovalStatus;
import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.CropSeasonStatus;
import HaUI.ImplSolution.DX_Farm.model.Parcel;
import HaUI.ImplSolution.DX_Farm.model.RiceVariety;
import HaUI.ImplSolution.DX_Farm.model.SeasonType;
import HaUI.ImplSolution.DX_Farm.model.TreatmentApplication;
import HaUI.ImplSolution.DX_Farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.DX_Farm.repository.ParcelRepository;
import HaUI.ImplSolution.DX_Farm.repository.RiceVarietyRepository;
import HaUI.ImplSolution.DX_Farm.repository.TreatmentApplicationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CropSeasonServiceTest {

  private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
  // 18:00 UTC on 6 Oct is already 01:00 on 7 Oct in Vietnam.
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T18:00:00Z"), VIETNAM);

  @Mock
  private CropSeasonRepository cropSeasonRepository;
  @Mock
  private ParcelRepository parcelRepository;
  @Mock
  private TreatmentApplicationRepository treatmentApplicationRepository;
  @Mock
  private RiceVarietyRepository riceVarietyRepository;
  @Mock
  private CurrentUserService currentUserService;
  @Mock
  private WorkflowService workflowService;
  @Mock
  private PendingRequestLimiter pendingRequestLimiter;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private CropSeasonService service;
  private Parcel parcel;

  @BeforeEach
  void setUp() {
    service = new CropSeasonService(cropSeasonRepository, parcelRepository,
        treatmentApplicationRepository, riceVarietyRepository, currentUserService, workflowService,
        new SeasonCalendar(List.of(11, 12, 1), List.of(3, 4, 5), List.of(6, 7, 8)),
        pendingRequestLimiter, eventPublisher, CLOCK);
    parcel = Parcel.builder().id(7L).code("P-01").farmerName("A")
        .approvalStatus(ApprovalStatus.APPROVED).build();
  }

  private void varietyExists() {
    when(riceVarietyRepository.findByCodeAndActiveTrue("OM5451")).thenReturn(
        Optional.of(RiceVariety.builder().code("OM5451").name("OM 5451").growthDaysMin(95)
            .growthDaysMax(100).source("test").reviewStatus("IN_REVIEW").build()));
  }

  private void parcelIsAccessible() {
    when(parcelRepository.findByCodeAndDeletedAtIsNull("P-01")).thenReturn(Optional.of(parcel));
    when(currentUserService.canAccess(parcel)).thenReturn(true);
  }

  @Test
  void createRejectsExpectedHarvestBeforeStart() {
    parcelIsAccessible();
    varietyExists();
    var request = new CropSeasonCreateRequest("OM5451", LocalDate.of(2026, 10, 1),
        SeasonType.THU_DONG, LocalDate.of(2026, 9, 30), null);

    assertThatThrownBy(() -> service.create("P-01", request, null))
        .isInstanceOf(IllegalArgumentException.class);
    verify(cropSeasonRepository, never()).saveAndFlush(any());
  }

  @Test
  void createRejectsSecondActiveSeasonOnSameParcel() {
    parcelIsAccessible();
    varietyExists();
    when(cropSeasonRepository
        .existsByParcel_IdAndStatusAndDeletedAtIsNullAndApprovalStatusNotAndIdNot(7L,
            CropSeasonStatus.ACTIVE, ApprovalStatus.REJECTED, -1L))
        .thenReturn(true);
    var request = new CropSeasonCreateRequest("OM5451", LocalDate.of(2026, 10, 1),
        SeasonType.THU_DONG, null, null);

    assertThatThrownBy(() -> service.create("P-01", request, null))
        .isInstanceOf(InvalidStateException.class);
    verify(cropSeasonRepository, never()).saveAndFlush(any());
  }

  @Test
  void harvestUsesVietnamDateForIsolationLock() {
    CropSeason season = CropSeason.builder().id(1L).parcel(parcel).riceVariety("OM 5451")
        .startDate(LocalDate.of(2026, 7, 1)).status(CropSeasonStatus.ACTIVE)
        .approvalStatus(ApprovalStatus.APPROVED).build();
    when(cropSeasonRepository.findById(1L)).thenReturn(Optional.of(season));
    when(currentUserService.canAccess(parcel)).thenReturn(true);
    // The lock ends on the Vietnamese "today"; a UTC-based date (6 Oct) would still block it.
    TreatmentApplication application =
        TreatmentApplication.builder().harvestLockedUntil(LocalDate.of(2026, 10, 7)).build();
    when(treatmentApplicationRepository.findTopByCropSeasonIdOrderByHarvestLockedUntilDesc(1L))
        .thenReturn(Optional.of(application));
    when(cropSeasonRepository.save(season)).thenReturn(season);

    var response = service.harvest(1L);

    assertThat(response.status()).isEqualTo("HARVESTED");
  }

  @Test
  void harvestIsBlockedWhileIsolationLockIsAfterVietnamToday() {
    CropSeason season = CropSeason.builder().id(1L).parcel(parcel).riceVariety("OM 5451")
        .startDate(LocalDate.of(2026, 7, 1)).status(CropSeasonStatus.ACTIVE)
        .approvalStatus(ApprovalStatus.APPROVED).build();
    when(cropSeasonRepository.findById(1L)).thenReturn(Optional.of(season));
    when(currentUserService.canAccess(parcel)).thenReturn(true);
    TreatmentApplication application =
        TreatmentApplication.builder().harvestLockedUntil(LocalDate.of(2026, 10, 8)).build();
    when(treatmentApplicationRepository.findTopByCropSeasonIdOrderByHarvestLockedUntilDesc(1L))
        .thenReturn(Optional.of(application));

    assertThatThrownBy(() -> service.harvest(1L)).isInstanceOf(PokaYokeViolationException.class);
    assertThat(season.getStatus()).isEqualTo(CropSeasonStatus.ACTIVE);
  }
}
