package HaUI.ImplSolution.DX_Farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.DX_Farm.exception.PokaYokeViolationException;
import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.CropSeasonStatus;
import HaUI.ImplSolution.DX_Farm.model.Parcel;
import HaUI.ImplSolution.DX_Farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiseaseReportRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class DiseaseReportServiceTest {

  @Mock
  private DiseaseReportRepository diseaseReportRepository;
  @Mock
  private CropSeasonRepository cropSeasonRepository;
  @Mock
  private DiagnosisRepository diagnosisRepository;
  @Mock
  private FileStorageService fileStorageService;
  @Mock
  private CurrentUserService currentUserService;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Test
  void createIsRejectedOnHarvestedSeasonWithoutStoringTheImage() {
    Parcel parcel = Parcel.builder().id(1L).code("P-01").farmerName("A").build();
    CropSeason season =
        CropSeason.builder().id(5L).parcel(parcel).status(CropSeasonStatus.HARVESTED).build();
    when(cropSeasonRepository.findById(5L)).thenReturn(Optional.of(season));
    when(currentUserService.canAccess(parcel)).thenReturn(true);
    when(currentUserService.currentFullName()).thenReturn("A");
    var service = new DiseaseReportService(diseaseReportRepository, cropSeasonRepository,
        diagnosisRepository, fileStorageService, currentUserService, eventPublisher);
    var image = new MockMultipartFile("image", "leaf.jpg", "image/jpeg", new byte[] {1});

    assertThatThrownBy(() -> service.create(5L, image, null)).isInstanceOfSatisfying(
        PokaYokeViolationException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo("CROP_SEASON_HARVESTED"));
    verify(fileStorageService, never()).store(any());
    verify(diseaseReportRepository, never()).saveAndFlush(any());
  }
}
