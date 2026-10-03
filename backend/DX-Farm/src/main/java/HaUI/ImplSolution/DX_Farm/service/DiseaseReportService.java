package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.storage.StorageService;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisResponse;
import HaUI.ImplSolution.DX_Farm.dto.DiseaseReportResponse;
import HaUI.ImplSolution.DX_Farm.event.DiseaseReportCreatedEvent;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import HaUI.ImplSolution.DX_Farm.exception.PokaYokeViolationException;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.ApprovalStatus;
import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.CropSeasonStatus;
import HaUI.ImplSolution.DX_Farm.model.Diagnosis;
import HaUI.ImplSolution.DX_Farm.model.DiseaseReport;
import HaUI.ImplSolution.DX_Farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiseaseReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
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
  private final StorageService storageService;
  private final CurrentUserService currentUserService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public DiseaseReportService(DiseaseReportRepository diseaseReportRepository,
      CropSeasonRepository cropSeasonRepository, DiagnosisRepository diagnosisRepository,
      FileStorageService fileStorageService, StorageService storageService,
      CurrentUserService currentUserService, ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.diseaseReportRepository = diseaseReportRepository;
    this.cropSeasonRepository = cropSeasonRepository;
    this.diagnosisRepository = diagnosisRepository;
    this.fileStorageService = fileStorageService;
    this.storageService = storageService;
    this.currentUserService = currentUserService;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  public List<DiseaseReportResponse> listByCropSeason(Long cropSeasonId, boolean includeDeleted) {
    if (includeDeleted && !currentUserService.isAdmin()) {
      throw new AccessDeniedException("includeDeleted is limited to ADMIN");
    }
    boolean farmerView = currentUserService.isFarmer();
    findAccessibleSeasonOrThrow(cropSeasonId);
    List<DiseaseReport> reports = includeDeleted
        ? diseaseReportRepository.findByCropSeasonIdOrderByIdAsc(cropSeasonId)
        : diseaseReportRepository.findByCropSeasonIdAndDeletedAtIsNullOrderByIdAsc(cropSeasonId);
    return reports.stream().map(report -> toResponse(report, farmerView)).toList();
  }

  public DiseaseReportResponse getById(Long id) {
    return toResponse(findOrThrow(id), currentUserService.isFarmer());
  }

  @Transactional
  public DiseaseReportResponse create(Long cropSeasonId, MultipartFile image,
      UUID clientRequestId) {
    CropSeason season = findAccessibleSeasonOrThrow(cropSeasonId);
    String reporter = currentUserService.currentFullName();
    String reporterId = currentUserService.currentUserId();
    if (clientRequestId != null) {
      var existing = diseaseReportRepository.findByClientRequestId(clientRequestId);
      if (existing.isPresent()) {
        return toResponse(requireSameRequester(existing.get(), cropSeasonId, reporter, reporterId),
            currentUserService.isFarmer());
      }
    }
    if (season.getStatus() == CropSeasonStatus.HARVESTED) {
      throw new PokaYokeViolationException("CROP_SEASON_HARVESTED",
          "Crop season is already harvested: " + cropSeasonId);
    }
    String imageRef = fileStorageService.store(image, reporterId);
    DiseaseReport report = DiseaseReport.builder().cropSeason(season).imageRef(imageRef)
        .reportedBy(reporter).reportedByUserId(reporterId).clientRequestId(clientRequestId).build();
    DiseaseReport saved;
    try {
      saved = diseaseReportRepository.saveAndFlush(report);
    } catch (DataIntegrityViolationException e) {
      if (clientRequestId == null) {
        throw e;
      }
      throw new InvalidStateException("Idempotency-Key is already being processed");
    }
    eventPublisher.publishEvent(new DiseaseReportCreatedEvent(saved.getId(),
        season.getParcel().getCode(), season.getRiceVariety(), saved.getReportedBy(),
        saved.getReportedAt(), saved.getImageRef()));
    return toResponse(saved, currentUserService.isFarmer());
  }

  // Reports created before user ids were stored fall back to comparing display names.
  private static DiseaseReport requireSameRequester(DiseaseReport report, Long cropSeasonId,
      String reporter, String reporterId) {
    boolean sameUser =
        report.getReportedByUserId() != null ? report.getReportedByUserId().equals(reporterId)
            : report.getReportedBy().equals(reporter);
    if (!report.getCropSeason().getId().equals(cropSeasonId) || !sameUser) {
      throw new InvalidStateException("Idempotency-Key was used for a different request");
    }
    return report;
  }

  @Transactional
  public void delete(Long id) {
    DiseaseReport report = findOrThrow(id);
    report.setDeletedAt(Instant.now(clock));
    diseaseReportRepository.save(report);
  }

  // A season is reachable only while neither it nor its parcel is soft-deleted.
  private CropSeason findAccessibleSeasonOrThrow(Long cropSeasonId) {
    return cropSeasonRepository.findById(cropSeasonId)
        .filter(season -> season.getDeletedAt() == null)
        .filter(season -> season.getParcel().getDeletedAt() == null)
        .filter(season -> currentUserService.canAccess(season.getParcel()))
        .orElseThrow(() -> new ResourceNotFoundException("Crop season not found: " + cropSeasonId));
  }

  private DiseaseReport findOrThrow(Long id) {
    return diseaseReportRepository.findById(id).filter(report -> report.getDeletedAt() == null)
        .filter(report -> report.getCropSeason().getDeletedAt() == null)
        .filter(report -> report.getCropSeason().getParcel().getDeletedAt() == null)
        .filter(report -> currentUserService.canAccess(report.getCropSeason().getParcel()))
        .orElseThrow(() -> new ResourceNotFoundException("Disease report not found: " + id));
  }

  private DiseaseReportResponse toResponse(DiseaseReport report, boolean farmerView) {
    var diagnosisView = diagnosisRepository.findByDiseaseReportId(report.getId())
        .map(diagnosis -> toDiagnosisView(diagnosis, farmerView)).orElse(null);
    return new DiseaseReportResponse(report.getId(), report.getCropSeason().getId(),
        storageService.presignedUrl(report.getImageRef()), report.getReportedBy(),
        report.getReportedAt(), diagnosisView);
  }

  // A farmer sees the clinical content only once the engineer has approved it.
  private DiagnosisResponse toDiagnosisView(Diagnosis diagnosis, boolean farmerView) {
    DiagnosisResponse full = DiagnosisService.toResponse(diagnosis);
    ApprovalStatus status = diagnosis.getApprovalStatus();
    if (!farmerView || status == ApprovalStatus.APPROVED) {
      return full;
    }
    boolean rejected = status == ApprovalStatus.REJECTED;
    return new DiagnosisResponse(full.id(), full.diseaseReportId(), null, null, null, null, null,
        null, full.approvalStatus(), null, rejected ? full.decidedAt() : null,
        rejected ? full.rejectionReason() : null, null);
  }
}
