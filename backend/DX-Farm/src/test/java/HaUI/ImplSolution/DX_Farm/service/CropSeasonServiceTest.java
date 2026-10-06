package HaUI.ImplSolution.DX_Farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.DX_Farm.dto.CropSeasonCreateRequest;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import HaUI.ImplSolution.DX_Farm.exception.PokaYokeViolationException;
import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.CropSeasonStatus;
import HaUI.ImplSolution.DX_Farm.model.Parcel;
import HaUI.ImplSolution.DX_Farm.model.TreatmentApplication;
import HaUI.ImplSolution.DX_Farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.DX_Farm.repository.ParcelRepository;
import HaUI.ImplSolution.DX_Farm.repository.TreatmentApplicationRepository;
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
  private CurrentUserService currentUserService;

  private CropSeasonService service;
  private Parcel parcel;

  @BeforeEach
  void setUp() {
    service = new CropSeasonService(cropSeasonRepository, parcelRepository,
        treatmentApplicationRepository, currentUserService, CLOCK);
    parcel = Parcel.builder().id(7L).code("P-01").farmerName("A").build();
  }

  private void parcelIsAccessible() {
    when(parcelRepository.findByCodeAndDeletedAtIsNull("P-01")).thenReturn(Optional.of(parcel));
    when(currentUserService.canAccess(parcel)).thenReturn(true);
  }

  @Test
  void createRejectsExpectedHarvestBeforeStart() {
    parcelIsAccessible();
    var request = new CropSeasonCreateRequest("OM 5451", LocalDate.of(2026, 10, 1),
        LocalDate.of(2026, 9, 30));

    assertThatThrownBy(() -> service.create("P-01", request))
        .isInstanceOf(IllegalArgumentException.class);
    verify(cropSeasonRepository, never()).saveAndFlush(any());
  }

  @Test
  void createRejectsSecondActiveSeasonOnSameParcel() {
    parcelIsAccessible();
    when(cropSeasonRepository.existsByParcel_IdAndStatusAndDeletedAtIsNull(7L,
        CropSeasonStatus.ACTIVE)).thenReturn(true);
    var request = new CropSeasonCreateRequest("OM 5451", LocalDate.of(2026, 10, 1), null);

    assertThatThrownBy(() -> service.create("P-01", request))
        .isInstanceOf(InvalidStateException.class);
    verify(cropSeasonRepository, never()).saveAndFlush(any());
  }

  @Test
  void harvestUsesVietnamDateForIsolationLock() {
    CropSeason season = CropSeason.builder().id(1L).parcel(parcel).riceVariety("OM 5451")
        .startDate(LocalDate.of(2026, 7, 1)).status(CropSeasonStatus.ACTIVE).build();
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
        .startDate(LocalDate.of(2026, 7, 1)).status(CropSeasonStatus.ACTIVE).build();
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
