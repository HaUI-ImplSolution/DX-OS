package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.DiseaseReportResponse;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.DiseaseReport;
import HaUI.ImplSolution.DX_Farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiseaseReportRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class DiseaseReportService {

  private final DiseaseReportRepository diseaseReportRepository;
  private final CropSeasonRepository cropSeasonRepository;
  private final DiagnosisRepository diagnosisRepository;
  private final FileStorageService fileStorageService;
  private final CurrentUserService currentUserService;

  public DiseaseReportService(DiseaseReportRepository diseaseReportRepository,
      CropSeasonRepository cropSeasonRepository, DiagnosisRepository diagnosisRepository,
      FileStorageService fileStorageService, CurrentUserService currentUserService) {
    this.diseaseReportRepository = diseaseReportRepository;
    this.cropSeasonRepository = cropSeasonRepository;
    this.diagnosisRepository = diagnosisRepository;
    this.fileStorageService = fileStorageService;
    this.currentUserService = currentUserService;
  }

  public List<DiseaseReportResponse> listByCropSeason(Long cropSeasonId) {
    return diseaseReportRepository.findByCropSeasonId(cropSeasonId).stream().map(this::toResponse)
        .toList();
  }

  public DiseaseReportResponse getById(Long id) {
    return toResponse(findOrThrow(id));
  }

  @Transactional
  public DiseaseReportResponse create(Long cropSeasonId, MultipartFile image) {
    CropSeason season = cropSeasonRepository.findById(cropSeasonId)
        .orElseThrow(() -> new ResourceNotFoundException("Crop season not found: " + cropSeasonId));
    String imageRef = fileStorageService.store(image);
    DiseaseReport report = DiseaseReport.builder().cropSeason(season).imageRef(imageRef)
        .reportedBy(currentUserService.currentFullName()).build();
    return toResponse(diseaseReportRepository.save(report));
  }

  private DiseaseReport findOrThrow(Long id) {
    return diseaseReportRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Disease report not found: " + id));
  }

  private DiseaseReportResponse toResponse(DiseaseReport report) {
    var diagnosis = diagnosisRepository.findByDiseaseReportId(report.getId())
        .map(DiagnosisService::toResponse).orElse(null);
    return new DiseaseReportResponse(report.getId(), report.getCropSeason().getId(),
        report.getImageRef(), report.getReportedBy(), report.getReportedAt(), diagnosis);
  }
}
