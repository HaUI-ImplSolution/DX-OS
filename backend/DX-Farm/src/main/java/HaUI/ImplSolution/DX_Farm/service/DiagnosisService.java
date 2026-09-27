package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.AiDiagnoseRequest;
import HaUI.ImplSolution.DX_Farm.dto.AiDiagnoseRequest.FieldContext;
import HaUI.ImplSolution.DX_Farm.dto.AiDiagnoseRequest.RecentTreatment;
import HaUI.ImplSolution.DX_Farm.dto.AiDiagnoseResponse;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisApplyRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisCreateRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisRejectRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisResponse;
import HaUI.ImplSolution.DX_Farm.dto.TreatmentApplicationResponse;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DiagnosisService {

  private static final int RECENT_TREATMENTS_LIMIT = 5;
  private static final String TREATMENT_PATH = "treatment";
  private static final String REVIEW_REQUIRED_PATH = "review_required";
  private static final String LOW_CONFIDENCE_REVIEW_PATH = "low_confidence_review";

  private final DiagnosisRepository diagnosisRepository;
  private final DiseaseReportRepository diseaseReportRepository;
  private final DiseaseRepository diseaseRepository;
  private final TreatmentRuleService treatmentRuleService;
  private final TreatmentApplicationRepository treatmentApplicationRepository;
  private final CurrentUserService currentUserService;
  private final AiDiagnosisClient aiDiagnosisClient;
  private final String publicBaseUrl;
  private final BigDecimal confidenceThreshold;

  public DiagnosisService(DiagnosisRepository diagnosisRepository,
      DiseaseReportRepository diseaseReportRepository, DiseaseRepository diseaseRepository,
      TreatmentRuleService treatmentRuleService,
      TreatmentApplicationRepository treatmentApplicationRepository,
      CurrentUserService currentUserService, AiDiagnosisClient aiDiagnosisClient,
      @Value("${app.public-base-url}") String publicBaseUrl,
      @Value("${ai.service.confidence-threshold:0.70}") BigDecimal confidenceThreshold) {
    this.diagnosisRepository = diagnosisRepository;
    this.diseaseReportRepository = diseaseReportRepository;
    this.diseaseRepository = diseaseRepository;
    this.treatmentRuleService = treatmentRuleService;
    this.treatmentApplicationRepository = treatmentApplicationRepository;
    this.currentUserService = currentUserService;
    this.aiDiagnosisClient = aiDiagnosisClient;
    this.publicBaseUrl = publicBaseUrl;
    this.confidenceThreshold = confidenceThreshold;
  }

  // Gọi AI Service theo contract POST /api/v1/diagnose và trả nguyên response - chưa lưu
  // Diagnosis ở đây, xem createFromAi() cho bước đối chiếu version rule rồi lưu.
  public AiDiagnoseResponse requestAiDiagnosis(Long reportId) {
    DiseaseReport report = diseaseReportRepository.findById(reportId)
        .orElseThrow(() -> new ResourceNotFoundException("Disease report not found: " + reportId));
    return aiDiagnosisClient.diagnose(buildAiDiagnoseRequest(report, "req_" + UUID.randomUUID()));
  }

  // Gọi AI, đối chiếu version của rule AI dùng với treatment_rules hiện tại rồi mới lưu
  // Diagnosis - rule đã đổi trong lúc AI xử lý thì từ chối, không lưu recommendation cũ.
  @Transactional
  public DiagnosisResponse createFromAi(Long reportId) {
    return createFromAi(reportId, "req_" + UUID.randomUUID());
  }

  @Transactional
  public DiagnosisResponse createFromAi(Long reportId, String requestId) {
    DiseaseReport report = diseaseReportRepository.findById(reportId)
        .orElseThrow(() -> new ResourceNotFoundException("Disease report not found: " + reportId));
    if (diagnosisRepository.findByDiseaseReportId(reportId).isPresent()) {
      throw new InvalidStateException("Disease report already has a diagnosis: " + reportId);
    }

    AiDiagnoseResponse aiResponse =
        aiDiagnosisClient.diagnose(buildAiDiagnoseRequest(report, requestId));
    AiDiagnoseResponse.CvResult cvResult = aiResponse.cvResult();
    String diseaseCode = cvResult.diseaseCode();

    Disease disease = diseaseRepository.findByCode(diseaseCode).orElseThrow(
        () -> new ResourceNotFoundException("Unknown disease code from AI: " + diseaseCode));
    List<TreatmentRule> currentRules = treatmentRuleService.findByDiseaseCode(diseaseCode);
    TreatmentRule currentRule = resolveRule(aiResponse, currentRules);

    Diagnosis diagnosis =
        Diagnosis.builder().diseaseReport(report).disease(disease).confidence(cvResult.confidence())
            .modelVersion(cvResult.modelName() + " " + cvResult.modelVersion())
            .treatmentRule(currentRule).recommendationText(aiResponse.recommendation().summary())
            .approvalStatus(ApprovalStatus.PENDING).build();
    return toResponse(saveDiagnosis(diagnosis, reportId));
  }

  private TreatmentRule resolveRule(AiDiagnoseResponse aiResponse,
      List<TreatmentRule> currentRules) {
    if (!Boolean.TRUE.equals(aiResponse.recommendation().requiresEngineerApproval())) {
      throw new InvalidStateException("AI recommendation must require engineer approval");
    }
    String path = aiResponse.structuredContext().path();
    String selectedRuleId = aiResponse.recommendation().selectedRuleId();
    if (isLowConfidence(aiResponse.cvResult().confidence())) {
      if (!LOW_CONFIDENCE_REVIEW_PATH.equals(path) || selectedRuleId != null) {
        throw new InvalidStateException(
            "Low-confidence diagnosis must use low_confidence_review without rule_id");
      }
      return null;
    }

    if (currentRules.size() != 1) {
      if (!REVIEW_REQUIRED_PATH.equals(path) || selectedRuleId != null) {
        throw new InvalidStateException(
            "Diagnosis without exactly one current rule must use review_required without rule_id");
      }
      return null;
    }

    TreatmentRule currentRule = currentRules.getFirst();
    if (!TREATMENT_PATH.equals(path) || selectedRuleId == null) {
      throw new InvalidStateException(
          "Diagnosis with exactly one current rule must select it on treatment path");
    }
    Long parsedRuleId;
    try {
      parsedRuleId = Long.valueOf(selectedRuleId);
    } catch (NumberFormatException exception) {
      throw new InvalidStateException("AI selected an invalid rule_id: " + selectedRuleId);
    }
    if (!Objects.equals(currentRule.getId(), parsedRuleId)) {
      throw new InvalidStateException("AI selected an unknown rule_id: " + selectedRuleId);
    }
    if (aiResponse.structuredContext().rules().size() != 1) {
      throw new InvalidStateException("Treatment path must contain exactly one structured rule");
    }
    AiDiagnoseResponse.RuleFact selectedFact = aiResponse.structuredContext().rules().getFirst();
    if (!Objects.equals(selectedFact.ruleId(), selectedRuleId)) {
      throw new InvalidStateException(
          "AI selected an unknown structured rule_id: " + selectedRuleId);
    }
    if (!Objects.equals(currentRule.getVersion(), selectedFact.version())) {
      throw new InvalidStateException(
          "Treatment rule for %s changed while AI was processing (rule version %s -> %s) - retry diagnosis"
              .formatted(currentRule.getDisease().getCode(), selectedFact.version(),
                  currentRule.getVersion()));
    }
    return currentRule;
  }

  private AiDiagnoseRequest buildAiDiagnoseRequest(DiseaseReport report, String requestId) {
    CropSeason season = report.getCropSeason();
    List<RecentTreatment> recentTreatments = treatmentApplicationRepository
        .findByCropSeasonIdOrderByAppliedAtDesc(season.getId()).stream()
        .limit(RECENT_TREATMENTS_LIMIT).map(DiagnosisService::toRecentTreatment).toList();
    FieldContext fieldContext = new FieldContext(season.getParcel().getCode(),
        season.getRiceVariety(), null, recentTreatments);
    String imageUrl = publicBaseUrl + "/uploads/" + report.getImageRef();
    return new AiDiagnoseRequest(requestId, season.getId(), imageUrl, fieldContext);
  }

  private static RecentTreatment toRecentTreatment(TreatmentApplication application) {
    Instant appliedAt = application.getAppliedAt().atStartOfDay(ZoneId.systemDefault()).toInstant();
    return new RecentTreatment(application.getPesticide().getName(), appliedAt,
        application.getHarvestLockedUntil());
  }

  @Transactional
  public DiagnosisResponse create(Long reportId, DiagnosisCreateRequest request) {
    DiseaseReport report = diseaseReportRepository.findById(reportId)
        .orElseThrow(() -> new ResourceNotFoundException("Disease report not found: " + reportId));
    if (diagnosisRepository.findByDiseaseReportId(reportId).isPresent()) {
      throw new InvalidStateException("Disease report already has a diagnosis: " + reportId);
    }

    Disease disease = diseaseRepository.findByCode(request.diseaseCode()).orElseThrow(
        () -> new ResourceNotFoundException("Unknown disease code: " + request.diseaseCode()));
    List<TreatmentRule> rules = treatmentRuleService.findByDiseaseCode(request.diseaseCode());
    TreatmentRule rule =
        !isLowConfidence(request.confidence()) && rules.size() == 1 ? rules.getFirst() : null;

    Diagnosis diagnosis = Diagnosis.builder().diseaseReport(report).disease(disease)
        .confidence(request.confidence()).modelVersion(request.modelVersion()).treatmentRule(rule)
        .recommendationText(
            buildRecommendation(report, disease, rule, request.confidence(), rules.size()))
        .approvalStatus(ApprovalStatus.PENDING).build();
    return toResponse(saveDiagnosis(diagnosis, reportId));
  }

  @Transactional
  public DiagnosisResponse approve(Long reportId) {
    Diagnosis diagnosis = findByReportIdOrThrow(reportId);
    requirePending(diagnosis);
    diagnosis.setApprovalStatus(ApprovalStatus.APPROVED);
    diagnosis.setDecidedBy(currentUserService.currentFullName());
    diagnosis.setDecidedAt(Instant.now());
    return toResponse(diagnosisRepository.save(diagnosis));
  }

  @Transactional
  public DiagnosisResponse reject(Long reportId, DiagnosisRejectRequest request) {
    Diagnosis diagnosis = findByReportIdOrThrow(reportId);
    requirePending(diagnosis);
    diagnosis.setApprovalStatus(ApprovalStatus.REJECTED);
    diagnosis.setDecidedBy(currentUserService.currentFullName());
    diagnosis.setDecidedAt(Instant.now());
    diagnosis.setRejectionReason(request.reason());
    return toResponse(diagnosisRepository.save(diagnosis));
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
    LocalDate appliedAt = request.appliedAt() != null ? request.appliedAt() : LocalDate.now();
    TreatmentApplication application =
        TreatmentApplication.builder().cropSeason(diagnosis.getDiseaseReport().getCropSeason())
            .diagnosis(diagnosis).pesticide(rule.getPesticide()).doseAmountSnapshot(actualDose)
            .doseUnitSnapshot(rule.getDoseUnit()).phiDaysSnapshot(rule.getPhiDays())
            .appliedAt(appliedAt).harvestLockedUntil(appliedAt.plusDays(rule.getPhiDays())).build();
    return toApplicationResponse(treatmentApplicationRepository.save(application));
  }

  private Diagnosis findByReportIdOrThrow(Long reportId) {
    return diagnosisRepository.findByDiseaseReportId(reportId).orElseThrow(
        () -> new ResourceNotFoundException("Diagnosis not found for report: " + reportId));
  }

  private static void requirePending(Diagnosis diagnosis) {
    if (diagnosis.getApprovalStatus() != ApprovalStatus.PENDING) {
      throw new InvalidStateException("Diagnosis is not pending approval: " + diagnosis.getId());
    }
  }

  private String buildRecommendation(DiseaseReport report, Disease disease, TreatmentRule rule,
      BigDecimal confidence, int ruleCount) {
    if (isLowConfidence(confidence)) {
      return "Thửa %s có tín hiệu %s với độ tin cậy %.1f%%, thấp hơn ngưỡng %.1f%%. Cần chụp lại ảnh rõ hơn hoặc nhờ kỹ sư đánh giá; chưa chọn quy tắc điều trị."
          .formatted(report.getCropSeason().getParcel().getCode(), disease.getName(),
              confidence.multiply(BigDecimal.valueOf(100)),
              confidenceThreshold.multiply(BigDecimal.valueOf(100)));
    }
    if (rule == null) {
      String reason = ruleCount == 0 ? "chưa có quy tắc điều trị"
          : "có nhiều quy tắc nhưng chưa có tiêu chí lựa chọn xác định";
      return "Thửa %s phát hiện bệnh %s (%.1f%%), nhưng %s. Cần kỹ sư nông nghiệp đánh giá; hệ thống không tự chọn thuốc."
          .formatted(report.getCropSeason().getParcel().getCode(), disease.getName(),
              confidence.multiply(BigDecimal.valueOf(100)), reason);
    }
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

  public DiagnosisResponse toResponse(Diagnosis diagnosis) {
    return new DiagnosisResponse(diagnosis.getId(), diagnosis.getDiseaseReport().getId(),
        diagnosis.getDisease().getCode(), diagnosis.getDisease().getName(),
        diagnosis.getConfidence(), diagnosis.getModelVersion(), recommendationPath(diagnosis),
        confidenceThreshold, diagnosis.getDiagnosedAt(), diagnosis.getRecommendationText(),
        diagnosis.getApprovalStatus().name(), diagnosis.getDecidedBy(), diagnosis.getDecidedAt(),
        diagnosis.getRejectionReason(),
        diagnosis.getTreatmentRule() != null
            ? TreatmentRuleService.toResponse(diagnosis.getTreatmentRule())
            : null);
  }

  private boolean isLowConfidence(BigDecimal confidence) {
    return confidence.compareTo(confidenceThreshold) < 0;
  }

  private String recommendationPath(Diagnosis diagnosis) {
    if (isLowConfidence(diagnosis.getConfidence())) {
      return LOW_CONFIDENCE_REVIEW_PATH;
    }
    return diagnosis.getTreatmentRule() == null ? REVIEW_REQUIRED_PATH : TREATMENT_PATH;
  }

  private Diagnosis saveDiagnosis(Diagnosis diagnosis, Long reportId) {
    try {
      return diagnosisRepository.saveAndFlush(diagnosis);
    } catch (DataIntegrityViolationException exception) {
      throw new InvalidStateException("Disease report already has a diagnosis: " + reportId);
    }
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
