package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.DiagnosisApplyRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisCreateRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisRejectRequest;
import HaUI.ImplSolution.DX_Farm.dto.DiagnosisResponse;
import HaUI.ImplSolution.DX_Farm.dto.TreatmentApplicationResponse;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.ApprovalStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DiagnosisService {

  private final DiagnosisRepository diagnosisRepository;
  private final DiseaseReportRepository diseaseReportRepository;
  private final DiseaseRepository diseaseRepository;
  private final TreatmentRuleService treatmentRuleService;
  private final TreatmentApplicationRepository treatmentApplicationRepository;
  private final CurrentUserService currentUserService;

  public DiagnosisService(DiagnosisRepository diagnosisRepository,
      DiseaseReportRepository diseaseReportRepository, DiseaseRepository diseaseRepository,
      TreatmentRuleService treatmentRuleService,
      TreatmentApplicationRepository treatmentApplicationRepository,
      CurrentUserService currentUserService) {
    this.diagnosisRepository = diagnosisRepository;
    this.diseaseReportRepository = diseaseReportRepository;
    this.diseaseRepository = diseaseRepository;
    this.treatmentRuleService = treatmentRuleService;
    this.treatmentApplicationRepository = treatmentApplicationRepository;
    this.currentUserService = currentUserService;
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
    TreatmentRule rule = treatmentRuleService.findByDiseaseCodeOrThrow(request.diseaseCode());

    Diagnosis diagnosis = Diagnosis.builder().diseaseReport(report).disease(disease)
        .confidence(request.confidence()).modelVersion(request.modelVersion()).treatmentRule(rule)
        .recommendationText(buildRecommendation(report, disease, rule, request.confidence()))
        .approvalStatus(ApprovalStatus.PENDING).build();
    return toResponse(diagnosisRepository.save(diagnosis));
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

    LocalDate appliedAt = request.appliedAt() != null ? request.appliedAt() : LocalDate.now();
    TreatmentRule rule = diagnosis.getTreatmentRule();
    TreatmentApplication application = TreatmentApplication.builder()
        .cropSeason(diagnosis.getDiseaseReport().getCropSeason()).diagnosis(diagnosis)
        .pesticide(rule.getPesticide()).doseAmountSnapshot(rule.getDoseAmount())
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

  private static String buildRecommendation(DiseaseReport report, Disease disease,
      TreatmentRule rule, BigDecimal confidence) {
    return "Thửa %s phát hiện bệnh %s (%.1f%%). Đề xuất phác đồ: phun %s %s %s - yêu cầu cách ly thu hoạch %d ngày."
        .formatted(report.getCropSeason().getParcel().getCode(), disease.getName(),
            confidence.multiply(BigDecimal.valueOf(100)), rule.getPesticide().getName(),
            rule.getDoseAmount(), rule.getDoseUnit(), rule.getPhiDays());
  }

  public static DiagnosisResponse toResponse(Diagnosis diagnosis) {
    return new DiagnosisResponse(diagnosis.getId(), diagnosis.getDiseaseReport().getId(),
        diagnosis.getDisease().getCode(), diagnosis.getDisease().getName(),
        diagnosis.getConfidence(), diagnosis.getModelVersion(), diagnosis.getDiagnosedAt(),
        diagnosis.getRecommendationText(), diagnosis.getApprovalStatus().name(),
        diagnosis.getDecidedBy(), diagnosis.getDecidedAt(), diagnosis.getRejectionReason(),
        TreatmentRuleService.toResponse(diagnosis.getTreatmentRule()));
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
