// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowOperations;
import HaUI.ImplSolution.dxos.farm.dto.CropSeasonCreateRequest;
import HaUI.ImplSolution.dxos.farm.dto.HarvestRequest;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.farm.model.ApprovalStatus;
import HaUI.ImplSolution.dxos.farm.model.CropSeason;
import HaUI.ImplSolution.dxos.farm.model.CropSeasonStatus;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import HaUI.ImplSolution.dxos.farm.model.RiceVariety;
import HaUI.ImplSolution.dxos.farm.model.SeasonType;
import HaUI.ImplSolution.dxos.farm.model.TreatmentApplication;
import HaUI.ImplSolution.dxos.farm.model.TreatmentConfirmationStatus;
import HaUI.ImplSolution.dxos.farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.dxos.farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.dxos.farm.repository.DiseaseReportRepository;
import HaUI.ImplSolution.dxos.farm.repository.HarvestRescheduleRepository;
import HaUI.ImplSolution.dxos.farm.repository.ParcelRepository;
import HaUI.ImplSolution.dxos.farm.repository.RiceVarietyRepository;
import HaUI.ImplSolution.dxos.farm.repository.TreatmentApplicationRepository;
import java.math.BigDecimal;
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
  private DiagnosisRepository diagnosisRepository;
  @Mock
  private DiseaseReportRepository diseaseReportRepository;
  @Mock
  private HarvestRescheduleRepository harvestRescheduleRepository;
  @Mock
  private NotificationService notificationService;
  @Mock
  private CurrentUser currentUserService;
  @Mock
  private WorkflowOperations workflowService;
  @Mock
  private PendingRequestLimiter pendingRequestLimiter;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private static final HarvestRequest HARVEST =
      new HarvestRequest(null, new BigDecimal("1000"), null, null, null, null);

  private CropSeasonService service;
  private Parcel parcel;

  @BeforeEach
  void setUp() {
    service = new CropSeasonService(cropSeasonRepository, parcelRepository,
        treatmentApplicationRepository, riceVarietyRepository, diagnosisRepository,
        diseaseReportRepository, harvestRescheduleRepository, notificationService,
        currentUserService, workflowService,
        new SeasonCalendar(List.of(11, 12, 1), List.of(3, 4, 5), List.of(6, 7, 8)),
        pendingRequestLimiter, eventPublisher, CLOCK);
    parcel = Parcel.builder().id(7L).code("P-01").farmerName("A")
        .areaHectares(new BigDecimal("1.00")).approvalStatus(ApprovalStatus.APPROVED).build();
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
    when(currentUserService.isFarmer()).thenReturn(true);
    // The lock ends on the Vietnamese "today"; a UTC-based date (6 Oct) would still block it.
    TreatmentApplication application =
        TreatmentApplication.builder().harvestLockedUntil(LocalDate.of(2026, 10, 7)).build();
    when(treatmentApplicationRepository
        .findTopByCropSeasonIdAndConfirmationStatusOrderByHarvestLockedUntilDesc(1L,
            TreatmentConfirmationStatus.CONFIRMED))
        .thenReturn(Optional.of(application));
    when(cropSeasonRepository.save(season)).thenReturn(season);

    var response = service.harvest(1L, HARVEST);

    assertThat(response.status()).isEqualTo("HARVESTED");
  }

  @Test
  void harvestIsBlockedWhileIsolationLockIsAfterVietnamToday() {
    CropSeason season = CropSeason.builder().id(1L).parcel(parcel).riceVariety("OM 5451")
        .startDate(LocalDate.of(2026, 7, 1)).status(CropSeasonStatus.ACTIVE)
        .approvalStatus(ApprovalStatus.APPROVED).build();
    when(cropSeasonRepository.findById(1L)).thenReturn(Optional.of(season));
    when(currentUserService.canAccess(parcel)).thenReturn(true);
    when(currentUserService.isFarmer()).thenReturn(true);
    TreatmentApplication application =
        TreatmentApplication.builder().harvestLockedUntil(LocalDate.of(2026, 10, 8)).build();
    when(treatmentApplicationRepository
        .findTopByCropSeasonIdAndConfirmationStatusOrderByHarvestLockedUntilDesc(1L,
            TreatmentConfirmationStatus.CONFIRMED))
        .thenReturn(Optional.of(application));

    assertThatThrownBy(() -> service.harvest(1L, HARVEST))
        .isInstanceOf(PokaYokeViolationException.class);
    assertThat(season.getStatus()).isEqualTo(CropSeasonStatus.ACTIVE);
  }
}
