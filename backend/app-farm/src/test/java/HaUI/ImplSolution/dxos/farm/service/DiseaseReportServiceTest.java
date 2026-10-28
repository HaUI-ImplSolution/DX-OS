// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.farm.model.ApprovalStatus;
import HaUI.ImplSolution.dxos.farm.model.CropSeason;
import HaUI.ImplSolution.dxos.farm.model.CropSeasonStatus;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import HaUI.ImplSolution.dxos.farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.dxos.farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.dxos.farm.repository.DiseaseReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
  private ObjectStorage storageService;
  @Mock
  private CurrentUser currentUserService;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private static org.springframework.transaction.PlatformTransactionManager transactionManager() {
    var manager =
        org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
    org.mockito.Mockito.when(manager.getTransaction(any()))
        .thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
    return manager;
  }

  @Test
  void createIsRejectedOnHarvestedSeasonWithoutStoringTheImage() {
    Parcel parcel = Parcel.builder().id(1L).code("P-01").farmerName("A").build();
    CropSeason season = CropSeason.builder().id(5L).parcel(parcel)
        .status(CropSeasonStatus.HARVESTED).approvalStatus(ApprovalStatus.APPROVED).build();
    when(cropSeasonRepository.findById(5L)).thenReturn(Optional.of(season));
    when(currentUserService.canAccess(parcel)).thenReturn(true);
    var service = new DiseaseReportService(diseaseReportRepository, cropSeasonRepository,
        diagnosisRepository, fileStorageService, storageService, currentUserService,
        org.mockito.Mockito.mock(NotificationService.class),
        org.mockito.Mockito.mock(HaUI.ImplSolution.dxos.core.api.workflow.WorkflowOperations.class),
        eventPublisher, Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneOffset.UTC),
        org.mockito.Mockito.mock(AiDiagnosisClient.class), transactionManager(),
        org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class));
    var image = new MockMultipartFile("image", "leaf.jpg", "image/jpeg", new byte[] {1});

    assertThatThrownBy(() -> service.create(5L, image, null)).isInstanceOfSatisfying(
        PokaYokeViolationException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo("CROP_SEASON_HARVESTED"));
    verify(fileStorageService, never()).store(any(), any());
    verify(diseaseReportRepository, never()).saveAndFlush(any());
  }

  @Test
  void farmerOnlySeesExplanationAfterApproval() {
    var parcel = Parcel.builder().id(1L).code("P-01").build();
    var season = CropSeason.builder().id(5L).parcel(parcel).build();
    var report = HaUI.ImplSolution.dxos.farm.model.DiseaseReport.builder().id(7L).cropSeason(season)
        .imageRef("image").reportedBy("demo").build();
    var disease = HaUI.ImplSolution.dxos.farm.model.Disease.builder().code("Rice___Blast")
        .name("Đạo ôn").build();
    var explanation = HaUI.ImplSolution.dxos.core.api.ai.GeneratedAnswer.fallback(true);
    var diagnosis = HaUI.ImplSolution.dxos.farm.model.Diagnosis.builder().id(1L)
        .diseaseReport(report).disease(disease).modelVersion("test")
        .confidence(new java.math.BigDecimal("0.91")).generatedExplanation(explanation).build();
    when(diseaseReportRepository.findById(7L)).thenReturn(Optional.of(report));
    when(diagnosisRepository.findTopByDiseaseReportIdOrderByIdDesc(7L))
        .thenReturn(Optional.of(diagnosis));
    when(currentUserService.canAccess(parcel)).thenReturn(true);
    when(currentUserService.isFarmer()).thenReturn(true);
    var service = new DiseaseReportService(diseaseReportRepository, cropSeasonRepository,
        diagnosisRepository, fileStorageService, storageService, currentUserService,
        org.mockito.Mockito.mock(NotificationService.class),
        org.mockito.Mockito.mock(HaUI.ImplSolution.dxos.core.api.workflow.WorkflowOperations.class),
        eventPublisher, Clock.systemUTC(), org.mockito.Mockito.mock(AiDiagnosisClient.class),
        org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class),
        org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class));
    for (ApprovalStatus status : ApprovalStatus.values()) {
      diagnosis.setApprovalStatus(status);
      var view = service.getById(7L).diagnosis();
      assertThat(view.generatedExplanation())
          .isEqualTo(status == ApprovalStatus.APPROVED ? explanation : null);
    }
  }
}
