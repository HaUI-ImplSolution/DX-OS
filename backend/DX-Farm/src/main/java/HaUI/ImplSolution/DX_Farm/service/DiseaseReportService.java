// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.storage.StorageService;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowActor;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowService;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowStatus;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisResponse;
import HaUI.ImplSolution.DX_Farm.dto.DiseaseReportResponse;
import HaUI.ImplSolution.DX_Farm.dto.ImagePrecheckResponse;
import HaUI.ImplSolution.DX_Farm.event.DiseaseReportCreatedEvent;
import HaUI.ImplSolution.DX_Farm.exception.AiTriageException;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import HaUI.ImplSolution.DX_Farm.exception.PokaYokeViolationException;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.ApprovalStatus;
import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.CropSeasonStatus;
import HaUI.ImplSolution.DX_Farm.model.Diagnosis;
import HaUI.ImplSolution.DX_Farm.model.DiseaseReport;
import HaUI.ImplSolution.DX_Farm.model.DiseaseReportStatus;
import HaUI.ImplSolution.DX_Farm.model.NotificationType;
import HaUI.ImplSolution.DX_Farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiseaseReportRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
  private final NotificationService notificationService;
  private final WorkflowService workflowService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;
  private final AiDiagnosisClient aiClient;
  private final TransactionTemplate readTransaction;
  private final TransactionTemplate writeTransaction;
  private final EntityManager entityManager;

  public DiseaseReportService(DiseaseReportRepository diseaseReportRepository,
      CropSeasonRepository cropSeasonRepository, DiagnosisRepository diagnosisRepository,
      FileStorageService fileStorageService, StorageService storageService,
      CurrentUserService currentUserService, NotificationService notificationService,
      WorkflowService workflowService, ApplicationEventPublisher eventPublisher, Clock clock,
      AiDiagnosisClient aiClient, PlatformTransactionManager transactionManager,
      EntityManager entityManager) {
    this.diseaseReportRepository = diseaseReportRepository;
    this.cropSeasonRepository = cropSeasonRepository;
    this.diagnosisRepository = diagnosisRepository;
    this.fileStorageService = fileStorageService;
    this.storageService = storageService;
    this.currentUserService = currentUserService;
    this.notificationService = notificationService;
    this.workflowService = workflowService;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
    this.aiClient = aiClient;
    this.readTransaction = new TransactionTemplate(transactionManager);
    this.readTransaction.setReadOnly(true);
    this.writeTransaction = new TransactionTemplate(transactionManager);
    this.entityManager = entityManager;
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
    if (reports.isEmpty()) {
      return List.of();
    }
    List<Long> reportIds = reports.stream().map(DiseaseReport::getId).toList();
    Map<Long, Diagnosis> latest = diagnosisRepository.findLatestForReports(reportIds).stream()
        .collect(Collectors.toMap(d -> d.getDiseaseReport().getId(), d -> d));
    Map<Long, List<Long>> retakes =
        diseaseReportRepository.findByRetakeOfIdInAndDeletedAtIsNullOrderByIdAsc(reportIds).stream()
            .collect(Collectors.groupingBy(report -> report.getRetakeOf().getId(),
                Collectors.mapping(DiseaseReport::getId, Collectors.toList())));
    return reports.stream().map(report -> toResponse(report, farmerView, latest.get(report.getId()),
        retakes.getOrDefault(report.getId(), List.of()))).toList();
  }

  public DiseaseReportResponse getById(Long id) {
    return toResponse(findOrThrow(id), currentUserService.isFarmer());
  }

  // Quality check only; nothing is stored.
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ImagePrecheckResponse precheck(MultipartFile image) {
    return aiClient.precheck(fileStorageService.sanitize(image));
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public DiseaseReportResponse create(Long cropSeasonId, MultipartFile image,
      UUID clientRequestId) {
    return create(cropSeasonId, image, clientRequestId, null);
  }

  // The photo quality is checked again here, outside any transaction, so a client that skipped
  // the precheck cannot store a poor photo.
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public DiseaseReportResponse create(Long cropSeasonId, MultipartFile image, UUID clientRequestId,
      Long retakeOf) {
    DiseaseReportResponse existing = readTransaction.execute(status -> {
      CropSeason season = findAccessibleSeasonOrThrow(cropSeasonId);
      DiseaseReport duplicate = findDuplicate(cropSeasonId, clientRequestId, retakeOf);
      if (duplicate != null) {
        return toResponse(duplicate, currentUserService.isFarmer());
      }
      requireActiveSeason(season);
      validateRetake(cropSeasonId, retakeOf, false);
      return null;
    });
    if (existing != null) {
      return existing;
    }
    ImagePrecheckResponse quality = precheck(image);
    if (!quality.passed()) {
      throw new AiTriageException(String.join(" ", quality.reasons()), null);
    }
    return writeTransaction
        .execute(status -> createChecked(cropSeasonId, image, clientRequestId, retakeOf, quality));
  }

  private DiseaseReportResponse createChecked(Long cropSeasonId, MultipartFile image,
      UUID clientRequestId, Long retakeOf, ImagePrecheckResponse quality) {
    CropSeason season = findAccessibleSeasonOrThrow(cropSeasonId);
    entityManager.lock(season, LockModeType.PESSIMISTIC_WRITE);
    DiseaseReport duplicate = findDuplicate(cropSeasonId, clientRequestId, retakeOf);
    if (duplicate != null) {
      return toResponse(duplicate, currentUserService.isFarmer());
    }
    requireActiveSeason(season);
    DiseaseReport original = validateRetake(cropSeasonId, retakeOf, true);
    String reporter = currentUserService.currentFullName();
    String reporterId = currentUserService.currentUserId();
    String imageRef = fileStorageService.store(image, reporterId);
    DiseaseReport report = DiseaseReport.builder().cropSeason(season).imageRef(imageRef)
        .reportedBy(reporter).reportedByUserId(reporterId).clientRequestId(clientRequestId)
        .retakeOf(original).qualityPassed(quality.passed()).qualitySharpness(quality.sharpness())
        .qualityBrightness(quality.brightness()).qualityWidth(quality.width())
        .qualityHeight(quality.height()).build();
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

  private DiseaseReport findDuplicate(Long seasonId, UUID key, Long retakeOf) {
    if (key == null) {
      return null;
    }
    return diseaseReportRepository.findByClientRequestId(key).map(report -> {
      DiseaseReport duplicate = requireSameRequester(report, seasonId,
          currentUserService.currentFullName(), currentUserService.currentUserId());
      Long originalId = report.getRetakeOf() == null ? null : report.getRetakeOf().getId();
      if (!Objects.equals(originalId, retakeOf)) {
        throw new InvalidStateException("Idempotency-Key was used for a different retake");
      }
      return duplicate;
    }).orElse(null);
  }

  private static void requireActiveSeason(CropSeason season) {
    if (season.getApprovalStatus() != ApprovalStatus.APPROVED) {
      throw new PokaYokeViolationException("CROP_SEASON_NOT_APPROVED",
          "Crop season is not approved yet: " + season.getId());
    }
    if (season.getStatus() == CropSeasonStatus.HARVESTED) {
      throw new PokaYokeViolationException("CROP_SEASON_HARVESTED",
          "Crop season is already harvested: " + season.getId());
    }
  }

  private DiseaseReport validateRetake(Long seasonId, Long originalId, boolean lock) {
    if (originalId == null) {
      return null;
    }
    DiseaseReport original = findOrThrow(originalId);
    if (lock) {
      entityManager.lock(original, LockModeType.PESSIMISTIC_WRITE);
    }
    if (!original.getCropSeason().getId().equals(seasonId)
        || original.getStatus() != DiseaseReportStatus.NEEDS_RETAKE) {
      throw new InvalidStateException(
          "retakeOf must refer to a NEEDS_RETAKE report in the same season");
    }
    if (!diseaseReportRepository.findByRetakeOfIdAndDeletedAtIsNullOrderByIdAsc(originalId)
        .isEmpty()) {
      throw new InvalidStateException("A replacement photo already exists for this report");
    }
    return original;
  }

  // Asks the farmer for a new photo; a diagnosis the engineer has not decided on stops waiting.
  @Transactional
  public DiseaseReportResponse requestRetake(Long id, String reason) {
    DiseaseReport report = findOrThrow(id);
    entityManager.lock(report, LockModeType.PESSIMISTIC_WRITE);
    requireActiveSeason(report.getCropSeason());
    if (report.getStatus() == DiseaseReportStatus.NEEDS_RETAKE) {
      throw new InvalidStateException("A new photo was already requested for report: " + id);
    }
    Diagnosis latest = diagnosisRepository.findTopByDiseaseReportIdOrderByIdDesc(id).orElse(null);
    if (latest != null && latest.getApprovalStatus() == ApprovalStatus.APPROVED) {
      throw new InvalidStateException(
          "An approved diagnosis cannot be replaced by a new photo: " + id);
    }
    if (latest != null && latest.getApprovalStatus() == ApprovalStatus.PENDING) {
      workflowService.recordTransition(DiagnosisService.CASE_TYPE, DiagnosisService.ENTITY_TYPE,
          latest.getId(), WorkflowStatus.CANCELLED,
          new WorkflowActor(currentUserService.currentUserId(),
              currentUserService.currentFullName()),
          "New photo requested", null);
    }
    report.setStatus(DiseaseReportStatus.NEEDS_RETAKE);
    report.setRetakeReason(reason);
    diseaseReportRepository.save(report);
    notificationService.notifyHousehold(report.getCropSeason().getParcel().getHousehold(),
        NotificationType.RETAKE_REQUESTED, "Kỹ sư cần ảnh mới",
        "Báo cáo #%d thửa %s. Lý do: %s. Hãy chụp lại và gửi ảnh thay thế.".formatted(id,
            report.getCropSeason().getParcel().getCode(), reason),
        "DISEASE_REPORT", String.valueOf(id));
    return toResponse(report, currentUserService.isFarmer());
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
    Diagnosis latest =
        diagnosisRepository.findTopByDiseaseReportIdOrderByIdDesc(report.getId()).orElse(null);
    List<Long> retakes =
        diseaseReportRepository.findByRetakeOfIdAndDeletedAtIsNullOrderByIdAsc(report.getId())
            .stream().map(DiseaseReport::getId).toList();
    return toResponse(report, farmerView, latest, retakes);
  }

  private DiseaseReportResponse toResponse(DiseaseReport report, boolean farmerView,
      Diagnosis latest, List<Long> retakes) {
    ImagePrecheckResponse quality = report.getQualityPassed() == null ? null
        : new ImagePrecheckResponse(report.getQualityPassed(), List.of(), report.getQualityWidth(),
            report.getQualityHeight(), report.getQualitySharpness(), report.getQualityBrightness());
    return new DiseaseReportResponse(report.getId(), report.getCropSeason().getId(),
        storageService.presignedUrl(report.getImageRef()), report.getReportedBy(),
        report.getReportedAt(), latest == null ? null : toDiagnosisView(latest, farmerView),
        report.getStatus().name(), report.getRetakeReason(),
        report.getRetakeOf() == null ? null : report.getRetakeOf().getId(), retakes, quality);
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
