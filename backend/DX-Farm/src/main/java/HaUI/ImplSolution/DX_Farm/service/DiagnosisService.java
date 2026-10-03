package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.storage.StorageService;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowActor;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowService;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowStatus;
import HaUI.ImplSolution.DX_Farm.dto.AiDiagnoseRequest;
import HaUI.ImplSolution.DX_Farm.dto.AiDiagnoseRequest.FieldContext;
import HaUI.ImplSolution.DX_Farm.dto.AiDiagnoseRequest.RecentTreatment;
import HaUI.ImplSolution.DX_Farm.dto.AiDiagnoseResponse;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisApplyRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisCreateRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisRejectRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisResponse;
import HaUI.ImplSolution.DX_Farm.dto.TreatmentApplicationResponse;
import HaUI.ImplSolution.DX_Farm.event.DiagnosisPendingEvent;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import HaUI.ImplSolution.DX_Farm.exception.PokaYokeViolationException;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.ApprovalStatus;
import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.Diagnosis;
import HaUI.ImplSolution.DX_Farm.model.Disease;
import HaUI.ImplSolution.DX_Farm.model.DiseaseReport;
import HaUI.ImplSolution.DX_Farm.model.TreatmentApplication;
import HaUI.ImplSolution.DX_Farm.model.TreatmentRule;
import HaUI.ImplSolution.DX_Farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiseaseRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiseaseReportRepository;
import HaUI.ImplSolution.DX_Farm.repository.TreatmentApplicationRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DiagnosisService {

  static final String CASE_TYPE = "DIAGNOSIS_APPROVAL";
  static final String ENTITY_TYPE = "DIAGNOSIS";
  // Must outlast the AI read timeout, since the AI service downloads the photo while it works.
  private static final Duration AI_IMAGE_URL_TTL = Duration.ofMinutes(10);
  private static final int RECENT_TREATMENTS_LIMIT = 5;
  // Reports of spraying older than this are rejected so the harvest lock cannot be defeated.
  private static final int MAX_APPLIED_BACKDATE_DAYS = 30;

  private final DiagnosisRepository diagnosisRepository;
  private final DiseaseReportRepository diseaseReportRepository;
  private final DiseaseRepository diseaseRepository;
  private final TreatmentRuleService treatmentRuleService;
  private final TreatmentApplicationRepository treatmentApplicationRepository;
  private final CurrentUserService currentUserService;
  private final WorkflowService workflowService;
  private final AiDiagnosisClient aiDiagnosisClient;
  private final ApplicationEventPublisher eventPublisher;
  private final StorageService storageService;
  private final Clock clock;
  private final EntityManager entityManager;

  public DiagnosisService(DiagnosisRepository diagnosisRepository,
      DiseaseReportRepository diseaseReportRepository, DiseaseRepository diseaseRepository,
      TreatmentRuleService treatmentRuleService,
      TreatmentApplicationRepository treatmentApplicationRepository,
      CurrentUserService currentUserService, WorkflowService workflowService,
      AiDiagnosisClient aiDiagnosisClient, ApplicationEventPublisher eventPublisher,
      StorageService storageService, Clock clock, EntityManager entityManager) {
    this.diagnosisRepository = diagnosisRepository;
    this.diseaseReportRepository = diseaseReportRepository;
    this.diseaseRepository = diseaseRepository;
    this.treatmentRuleService = treatmentRuleService;
    this.treatmentApplicationRepository = treatmentApplicationRepository;
    this.currentUserService = currentUserService;
    this.workflowService = workflowService;
    this.aiDiagnosisClient = aiDiagnosisClient;
    this.eventPublisher = eventPublisher;
    this.storageService = storageService;
    this.clock = clock;
    this.entityManager = entityManager;
  }

  // Gọi AI Service theo contract POST /api/v1/diagnose và trả nguyên response - chưa lưu
  // Diagnosis ở đây, xem createFromAi() cho bước đối chiếu version rule rồi lưu.
  public AiDiagnoseResponse requestAiDiagnosis(Long reportId) {
    DiseaseReport report = findActiveReportOrThrow(reportId);
    return aiDiagnosisClient.diagnose(buildAiDiagnoseRequest(report));
  }

  // Gọi AI, đối chiếu version của rule AI dùng với treatment_rules hiện tại rồi mới lưu
  // Diagnosis - rule đã đổi trong lúc AI xử lý thì từ chối, không lưu recommendation cũ.
  @Transactional
  public DiagnosisResponse createFromAi(Long reportId) {
    DiseaseReport report = findActiveReportOrThrow(reportId);
    if (diagnosisRepository.findByDiseaseReportId(reportId).isPresent()) {
      throw new InvalidStateException("Disease report already has a diagnosis: " + reportId);
    }

    AiDiagnoseResponse aiResponse = aiDiagnosisClient.diagnose(buildAiDiagnoseRequest(report));
    AiDiagnoseResponse.CvResult cvResult = aiResponse.cvResult();
    String diseaseCode = cvResult.diseaseCode();

    Disease disease = diseaseRepository.findByCodeAndActiveTrue(diseaseCode).orElseThrow(
        () -> new ResourceNotFoundException("Unknown disease code from AI: " + diseaseCode));
    AiDiagnoseResponse.RuleFact selectedRule = findSelectedRule(aiResponse);
    TreatmentRule currentRule = null;
    if (selectedRule != null) {
      Long selectedRuleId;
      try {
        selectedRuleId = Long.valueOf(selectedRule.ruleId());
      } catch (NumberFormatException exception) {
        throw new InvalidStateException("AI selected an invalid rule_id: " + selectedRule.ruleId());
      }
      currentRule = treatmentRuleService.findByIdAndDiseaseCodeOrThrow(selectedRuleId, diseaseCode);
      if (!Objects.equals(currentRule.getVersion(), selectedRule.version())) {
        throw new InvalidStateException(
            "Treatment rule for %s changed while AI was processing (rule version %s -> %s) - retry diagnosis"
                .formatted(diseaseCode, selectedRule.version(), currentRule.getVersion()));
      }
    }

    Diagnosis diagnosis =
        Diagnosis.builder().diseaseReport(report).disease(disease).confidence(cvResult.confidence())
            .modelVersion(cvResult.modelName() + " " + cvResult.modelVersion())
            .treatmentRule(currentRule).recommendationText(aiResponse.recommendation().summary())
            .approvalStatus(ApprovalStatus.PENDING).build();
    return savePending(diagnosis);
  }

  private static AiDiagnoseResponse.RuleFact findSelectedRule(AiDiagnoseResponse aiResponse) {
    String selectedRuleId = aiResponse.recommendation().selectedRuleId();
    if (selectedRuleId == null) {
      if (!"review_required".equals(aiResponse.structuredContext().path())
          || !aiResponse.structuredContext().rules().isEmpty()) {
        throw new InvalidStateException("AI omitted rule_id outside review_required path");
      }
      return null;
    }
    if (!"treatment".equals(aiResponse.structuredContext().path())) {
      throw new InvalidStateException("AI selected rule_id outside treatment path");
    }
    return aiResponse.structuredContext().rules().stream()
        .filter(rule -> rule.ruleId().equals(selectedRuleId)).findFirst().orElseThrow(
            () -> new InvalidStateException("AI selected an unknown rule_id: " + selectedRuleId));
  }

  private AiDiagnoseRequest buildAiDiagnoseRequest(DiseaseReport report) {
    CropSeason season = report.getCropSeason();
    List<RecentTreatment> recentTreatments =
        treatmentApplicationRepository.findByCropSeasonIdOrderByAppliedAtDesc(season.getId())
            .stream().limit(RECENT_TREATMENTS_LIMIT).map(this::toRecentTreatment).toList();
    FieldContext fieldContext = new FieldContext(season.getParcel().getCode(),
        season.getRiceVariety(), null, recentTreatments);
    String imageUrl = storageService.internalPresignedUrl(report.getImageRef(), AI_IMAGE_URL_TTL);
    return new AiDiagnoseRequest("req_" + UUID.randomUUID(), season.getId(), imageUrl,
        fieldContext);
  }

  private RecentTreatment toRecentTreatment(TreatmentApplication application) {
    Instant appliedAt = application.getAppliedAt().atStartOfDay(clock.getZone()).toInstant();
    return new RecentTreatment(application.getPesticide().getName(), appliedAt,
        application.getHarvestLockedUntil());
  }

  @Transactional
  public DiagnosisResponse create(Long reportId, DiagnosisCreateRequest request) {
    DiseaseReport report = findActiveReportOrThrow(reportId);
    if (diagnosisRepository.findByDiseaseReportId(reportId).isPresent()) {
      throw new InvalidStateException("Disease report already has a diagnosis: " + reportId);
    }

    Disease disease = diseaseRepository.findByCodeAndActiveTrue(request.diseaseCode()).orElseThrow(
        () -> new ResourceNotFoundException("Unknown disease code: " + request.diseaseCode()));
    TreatmentRule rule = treatmentRuleService.findByDiseaseCodeOrThrow(request.diseaseCode());

    Diagnosis diagnosis = Diagnosis.builder().diseaseReport(report).disease(disease)
        .confidence(request.confidence()).modelVersion(request.modelVersion()).treatmentRule(rule)
        .recommendationText(buildRecommendation(report, disease, rule, request.confidence()))
        .approvalStatus(ApprovalStatus.PENDING).build();
    return savePending(diagnosis);
  }

  // Stores a PENDING diagnosis and announces it once the transaction commits.
  private DiagnosisResponse savePending(Diagnosis diagnosis) {
    Diagnosis saved = diagnosisRepository.save(diagnosis);
    workflowService.createCase(CASE_TYPE, ENTITY_TYPE, saved.getId(), currentActor());
    DiseaseReport report = saved.getDiseaseReport();
    Disease disease = saved.getDisease();
    eventPublisher.publishEvent(new DiagnosisPendingEvent(report.getId(),
        report.getCropSeason().getParcel().getCode(), disease.getCode(), disease.getName(),
        saved.getConfidence(), saved.getTreatmentRule() != null));
    return toResponse(saved);
  }

  @Transactional
  public DiagnosisResponse approve(Long reportId) {
    Diagnosis diagnosis = findByReportIdOrThrow(reportId);
    requirePending(diagnosis);
    diagnosis.setApprovalStatus(ApprovalStatus.APPROVED);
    diagnosis.setDecidedBy(currentUserService.currentFullName());
    diagnosis.setDecidedByUserId(currentUserService.currentUserId());
    diagnosis.setDecidedAt(Instant.now(clock));
    Diagnosis saved = diagnosisRepository.save(diagnosis);
    workflowService.recordTransition(CASE_TYPE, ENTITY_TYPE, saved.getId(), WorkflowStatus.APPROVED,
        currentActor(), null, null);
    return toResponse(saved);
  }

  @Transactional
  public DiagnosisResponse reject(Long reportId, DiagnosisRejectRequest request) {
    Diagnosis diagnosis = findByReportIdOrThrow(reportId);
    requirePending(diagnosis);
    diagnosis.setApprovalStatus(ApprovalStatus.REJECTED);
    diagnosis.setDecidedBy(currentUserService.currentFullName());
    diagnosis.setDecidedByUserId(currentUserService.currentUserId());
    diagnosis.setDecidedAt(Instant.now(clock));
    diagnosis.setRejectionReason(request.reason());
    Diagnosis saved = diagnosisRepository.save(diagnosis);
    workflowService.recordTransition(CASE_TYPE, ENTITY_TYPE, saved.getId(), WorkflowStatus.REJECTED,
        currentActor(), request.reason(), null);
    return toResponse(saved);
  }

  private WorkflowActor currentActor() {
    return new WorkflowActor(currentUserService.currentUserId(),
        currentUserService.currentFullName());
  }

  @Transactional
  public TreatmentApplicationResponse apply(Long reportId, DiagnosisApplyRequest request) {
    Diagnosis diagnosis = findByReportIdOrThrow(reportId);
    if (diagnosis.getApprovalStatus() != ApprovalStatus.APPROVED) {
      throw new InvalidStateException(
          "Diagnosis must be approved before applying treatment: " + reportId);
    }
    if (treatmentApplicationRepository.findByDiagnosisId(diagnosis.getId()).isPresent()) {
      throw new InvalidStateException("Diagnosis already applied: " + reportId);
    }

    TreatmentRule rule = diagnosis.getTreatmentRule();
    if (rule == null) {
      throw new InvalidStateException(
          "Diagnosis has no approved treatment rule and cannot be applied: " + reportId);
    }
    BigDecimal actualDose = request.doseAmount();
    if (!rule.allowsDose(actualDose)) {
      throw new InvalidStateException("Applied dose %s must be between %s and %s %s"
          .formatted(actualDose, rule.getDoseMin(), rule.getDoseMax(), rule.getDoseUnit()));
    }
    LocalDate appliedAt = resolveAppliedAt(request.appliedAt());
    CropSeason season = diagnosis.getDiseaseReport().getCropSeason();
    // Bumps the season version so a concurrent harvest cannot slip past this new isolation lock.
    entityManager.lock(season, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    TreatmentApplication application = TreatmentApplication.builder().cropSeason(season)
        .diagnosis(diagnosis).pesticide(rule.getPesticide()).doseAmountSnapshot(actualDose)
        .doseUnitSnapshot(rule.getDoseUnit()).phiDaysSnapshot(rule.getPhiDays())
        .appliedAt(appliedAt).harvestLockedUntil(appliedAt.plusDays(rule.getPhiDays())).build();
    try {
      return toApplicationResponse(treatmentApplicationRepository.saveAndFlush(application));
    } catch (DataIntegrityViolationException e) {
      throw new InvalidStateException("Diagnosis already applied: " + reportId);
    }
  }

  private LocalDate resolveAppliedAt(LocalDate requested) {
    LocalDate today = LocalDate.now(clock);
    if (requested == null) {
      return today;
    }
    if (requested.isAfter(today)) {
      throw new PokaYokeViolationException("INVALID_APPLIED_AT",
          "appliedAt must not be in the future");
    }
    if (requested.isBefore(today.minusDays(MAX_APPLIED_BACKDATE_DAYS))) {
      throw new PokaYokeViolationException("INVALID_APPLIED_AT",
          "appliedAt must not be more than %d days in the past"
              .formatted(MAX_APPLIED_BACKDATE_DAYS));
    }
    return requested;
  }

  // A report is usable only while neither it, its season nor its parcel is soft-deleted.
  private DiseaseReport findActiveReportOrThrow(Long reportId) {
    return diseaseReportRepository.findById(reportId)
        .filter(report -> report.getDeletedAt() == null)
        .filter(report -> report.getCropSeason().getDeletedAt() == null)
        .filter(report -> report.getCropSeason().getParcel().getDeletedAt() == null)
        .orElseThrow(() -> new ResourceNotFoundException("Disease report not found: " + reportId));
  }

  private Diagnosis findByReportIdOrThrow(Long reportId) {
    findActiveReportOrThrow(reportId);
    return diagnosisRepository.findByDiseaseReportId(reportId).orElseThrow(
        () -> new ResourceNotFoundException("Diagnosis not found for report: " + reportId));
  }

  private static void requirePending(Diagnosis diagnosis) {
    if (diagnosis.getApprovalStatus() != ApprovalStatus.PENDING) {
      throw new InvalidStateException("Diagnosis is not pending approval: " + diagnosis.getId());
    }
  }

  private static String buildRecommendation(DiseaseReport report, Disease disease,
      TreatmentRule rule, BigDecimal confidence) {
    return "Thửa %s phát hiện bệnh %s (%.1f%%). Đề xuất phác đồ: phun %s %s %s - yêu cầu cách ly thu hoạch %d ngày."
        .formatted(report.getCropSeason().getParcel().getCode(), disease.getName(),
            confidence.multiply(BigDecimal.valueOf(100)), rule.getPesticide().getName(),
            formatDoseRange(rule), rule.getDoseUnit(), rule.getPhiDays());
  }

  private static String formatDoseRange(TreatmentRule rule) {
    if (rule.getDoseMin().compareTo(rule.getDoseMax()) == 0) {
      return rule.getDoseMin().toPlainString();
    }
    return "%s-%s".formatted(rule.getDoseMin().toPlainString(), rule.getDoseMax().toPlainString());
  }

  public static DiagnosisResponse toResponse(Diagnosis diagnosis) {
    return new DiagnosisResponse(diagnosis.getId(), diagnosis.getDiseaseReport().getId(),
        diagnosis.getDisease().getCode(), diagnosis.getDisease().getName(),
        diagnosis.getConfidence(), diagnosis.getModelVersion(), diagnosis.getDiagnosedAt(),
        diagnosis.getRecommendationText(), diagnosis.getApprovalStatus().name(),
        diagnosis.getDecidedBy(), diagnosis.getDecidedAt(), diagnosis.getRejectionReason(),
        diagnosis.getTreatmentRule() != null
            ? TreatmentRuleService.toResponse(diagnosis.getTreatmentRule())
            : null);
  }

  private static TreatmentApplicationResponse toApplicationResponse(
      TreatmentApplication application) {
    return new TreatmentApplicationResponse(application.getId(),
        application.getCropSeason().getId(),
        application.getDiagnosis() != null ? application.getDiagnosis().getId() : null,
        application.getPesticide().getCode(), application.getDoseAmountSnapshot(),
        application.getDoseUnitSnapshot(), application.getPhiDaysSnapshot(),
        application.getAppliedAt(), application.getHarvestLockedUntil());
  }
}
