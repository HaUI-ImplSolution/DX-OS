package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.model.DiagnosisJob;
import HaUI.ImplSolution.DX_Farm.model.DiagnosisJobStatus;
import HaUI.ImplSolution.DX_Farm.repository.DiagnosisJobRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosisJobStateService {

  public record ClaimedJob(Long reportId, String requestId) {}

  private final DiagnosisJobRepository diagnosisJobRepository;

  public DiagnosisJobStateService(DiagnosisJobRepository diagnosisJobRepository) {
    this.diagnosisJobRepository = diagnosisJobRepository;
  }

  @Transactional
  public ClaimedJob claim(String jobId) {
    DiagnosisJob job = diagnosisJobRepository.findById(jobId).orElse(null);
    if (job == null || job.getStatus() != DiagnosisJobStatus.QUEUED) {
      return null;
    }
    ClaimedJob claimed = new ClaimedJob(job.getDiseaseReport().getId(), job.getRequestId());
    job.setStatus(DiagnosisJobStatus.RUNNING);
    job.setStage("diagnosing");
    diagnosisJobRepository.save(job);
    return claimed;
  }

  @Transactional
  public void complete(String jobId) {
    DiagnosisJob job = diagnosisJobRepository.findById(jobId).orElse(null);
    if (job == null) {
      return;
    }
    job.setStatus(DiagnosisJobStatus.COMPLETED);
    job.setStage("completed");
    job.setCompletedAt(Instant.now());
    diagnosisJobRepository.save(job);
  }

  @Transactional
  public void fail(String jobId) {
    DiagnosisJob job = diagnosisJobRepository.findById(jobId).orElse(null);
    if (job == null) {
      return;
    }
    job.setStatus(DiagnosisJobStatus.FAILED);
    job.setStage("failed");
    job.setErrorMessage("AI diagnosis could not be completed");
    job.setCompletedAt(Instant.now());
    diagnosisJobRepository.save(job);
  }

  @Transactional
  public void requeueInterruptedJobs() {
    List<DiagnosisJob> interrupted =
        diagnosisJobRepository.findByStatusIn(List.of(DiagnosisJobStatus.RUNNING));
    for (DiagnosisJob job : interrupted) {
      job.setStatus(DiagnosisJobStatus.QUEUED);
      job.setStage("queued");
      job.setErrorMessage(null);
      diagnosisJobRepository.save(job);
    }
  }
}
