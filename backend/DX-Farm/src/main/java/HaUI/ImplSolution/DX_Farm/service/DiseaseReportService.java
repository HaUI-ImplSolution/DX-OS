package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.DiagnosisResponse;
import HaUI.ImplSolution.DX_Farm.dto.DiseaseReportResponse;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.ApprovalStatus;
import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.Diagnosis;
import HaUI.ImplSolution.DX_Farm.model.DiseaseReport;
import HaUI.ImplSolution.DX_Farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiseaseReportRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
    if (currentUserService.isFarmer()) {
      findAccessibleSeasonOrThrow(cropSeasonId);
    }
    return diseaseReportRepository.findByCropSeasonId(cropSeasonId).stream().map(this::toResponse)
        .toList();
  }

  public DiseaseReportResponse getById(Long id) {
    return toResponse(findOrThrow(id));
  }

  @Transactional
  public DiseaseReportResponse create(Long cropSeasonId, MultipartFile image,
      UUID clientRequestId) {
    CropSeason season = findAccessibleSeasonOrThrow(cropSeasonId);
    String reporter = currentUserService.currentFullName();
    if (clientRequestId != null) {
      var existing = diseaseReportRepository.findByClientRequestId(clientRequestId);
      if (existing.isPresent()) {
        return toResponse(requireSameRequester(existing.get(), cropSeasonId, reporter));
      }
    }
    String imageRef = fileStorageService.store(image);
    DiseaseReport report = DiseaseReport.builder().cropSeason(season).imageRef(imageRef)
        .reportedBy(reporter).clientRequestId(clientRequestId).build();
    try {
      return toResponse(diseaseReportRepository.saveAndFlush(report));
    } catch (DataIntegrityViolationException e) {
      throw new InvalidStateException("Idempotency-Key is already being processed");
    }
  }

  private static DiseaseReport requireSameRequester(DiseaseReport report, Long cropSeasonId,
      String reporter) {
    if (!report.getCropSeason().getId().equals(cropSeasonId)
        || !report.getReportedBy().equals(reporter)) {
      throw new InvalidStateException("Idempotency-Key was used for a different request");
    }
    return report;
  }

  private CropSeason findAccessibleSeasonOrThrow(Long cropSeasonId) {
    return cropSeasonRepository.findById(cropSeasonId)
        .filter(season -> currentUserService.canAccess(season.getParcel()))
        .orElseThrow(() -> new ResourceNotFoundException("Crop season not found: " + cropSeasonId));
  }

  private DiseaseReport findOrThrow(Long id) {
    return diseaseReportRepository.findById(id)
        .filter(report -> currentUserService.canAccess(report.getCropSeason().getParcel()))
        .orElseThrow(() -> new ResourceNotFoundException("Disease report not found: " + id));
  }

  private DiseaseReportResponse toResponse(DiseaseReport report) {
    var diagnosis = diagnosisRepository.findByDiseaseReportId(report.getId())
        .map(this::toDiagnosisView).orElse(null);
    return new DiseaseReportResponse(report.getId(), report.getCropSeason().getId(),
        report.getImageRef(), report.getReportedBy(), report.getReportedAt(), diagnosis);
  }

  // A farmer sees the clinical content only once the engineer has approved it.
  private DiagnosisResponse toDiagnosisView(Diagnosis diagnosis) {
    DiagnosisResponse full = DiagnosisService.toResponse(diagnosis);
    ApprovalStatus status = diagnosis.getApprovalStatus();
    if (!currentUserService.isFarmer() || status == ApprovalStatus.APPROVED) {
      return full;
    }
    boolean rejected = status == ApprovalStatus.REJECTED;
    return new DiagnosisResponse(full.id(), full.diseaseReportId(), null, null, null, null, null,
        null, full.approvalStatus(), null, rejected ? full.decidedAt() : null,
        rejected ? full.rejectionReason() : null, null);
  }
}
