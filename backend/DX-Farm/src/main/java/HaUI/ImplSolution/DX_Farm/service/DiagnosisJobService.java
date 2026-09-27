package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.DiagnosisJobResponse;
import HaUI.ImplSolution.DX_Farm.event.DiagnosisJobRequested;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.Diagnosis;
import HaUI.ImplSolution.DX_Farm.model.DiagnosisJob;
import HaUI.ImplSolution.DX_Farm.model.DiagnosisJobStatus;
import HaUI.ImplSolution.DX_Farm.model.DiseaseReport;
import HaUI.ImplSolution.DX_Farm.repository.DiagnosisJobRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiseaseReportRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DiagnosisJobService {

  private final DiagnosisJobRepository diagnosisJobRepository;
  private final DiagnosisRepository diagnosisRepository;
  private final DiseaseReportRepository diseaseReportRepository;
  private final DiagnosisService diagnosisService;
  private final ApplicationEventPublisher eventPublisher;

  public DiagnosisJobService(DiagnosisJobRepository diagnosisJobRepository,
      DiagnosisRepository diagnosisRepository, DiseaseReportRepository diseaseReportRepository,
      DiagnosisService diagnosisService, ApplicationEventPublisher eventPublisher) {
    this.diagnosisJobRepository = diagnosisJobRepository;
    this.diagnosisRepository = diagnosisRepository;
    this.diseaseReportRepository = diseaseReportRepository;
    this.diagnosisService = diagnosisService;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public DiagnosisJobResponse start(Long reportId) {
    DiagnosisJob existing = diagnosisJobRepository.findByDiseaseReportId(reportId).orElse(null);
    if (existing != null) {
      return toResponse(existing);
    }
    DiseaseReport report = diseaseReportRepository.findById(reportId)
        .orElseThrow(() -> new ResourceNotFoundException("Disease report not found: " + reportId));
    DiagnosisJob job =
        diagnosisJobRepository.saveAndFlush(DiagnosisJob.builder().id(UUID.randomUUID().toString())
            .diseaseReport(report).requestId("req_" + UUID.randomUUID())
            .status(DiagnosisJobStatus.QUEUED).stage("queued").build());
    eventPublisher.publishEvent(new DiagnosisJobRequested(job.getId()));
    return toResponse(job);
  }

  public DiagnosisJobResponse get(String jobId) {
    return toResponse(findOrThrow(jobId));
  }

  public DiagnosisJob findOrThrow(String jobId) {
    return diagnosisJobRepository.findById(jobId)
        .orElseThrow(() -> new ResourceNotFoundException("Diagnosis job not found: " + jobId));
  }

  private DiagnosisJobResponse toResponse(DiagnosisJob job) {
    Diagnosis diagnosis =
        diagnosisRepository.findByDiseaseReportId(job.getDiseaseReport().getId()).orElse(null);
    return new DiagnosisJobResponse(job.getId(), job.getDiseaseReport().getId(), job.getRequestId(),
        job.getStatus().name(), job.getStage(), job.getErrorMessage(), job.getCreatedAt(),
        job.getUpdatedAt(), job.getCompletedAt(),
        diagnosis != null ? diagnosisService.toResponse(diagnosis) : null);
  }
}
