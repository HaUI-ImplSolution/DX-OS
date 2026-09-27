package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.event.DiagnosisJobRequested;
import HaUI.ImplSolution.DX_Farm.model.DiagnosisJobStatus;
import HaUI.ImplSolution.DX_Farm.repository.DiagnosisJobRepository;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class DiagnosisJobWorker {

  private static final Logger log = LoggerFactory.getLogger(DiagnosisJobWorker.class);

  private final DiagnosisJobRepository diagnosisJobRepository;
  private final DiagnosisService diagnosisService;
  private final DiagnosisJobStateService diagnosisJobStateService;
  private final Executor diagnosisExecutor;

  public DiagnosisJobWorker(DiagnosisJobRepository diagnosisJobRepository,
      DiagnosisService diagnosisService, DiagnosisJobStateService diagnosisJobStateService,
      @Qualifier("diagnosisExecutor") Executor diagnosisExecutor) {
    this.diagnosisJobRepository = diagnosisJobRepository;
    this.diagnosisService = diagnosisService;
    this.diagnosisJobStateService = diagnosisJobStateService;
    this.diagnosisExecutor = diagnosisExecutor;
  }

  @Async("diagnosisExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRequested(DiagnosisJobRequested event) {
    process(event.jobId());
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recoverQueuedJobs() {
    diagnosisJobStateService.requeueInterruptedJobs();
    diagnosisJobRepository.findByStatusIn(List.of(DiagnosisJobStatus.QUEUED))
        .forEach(job -> diagnosisExecutor.execute(() -> process(job.getId())));
  }

  public void process(String jobId) {
    DiagnosisJobStateService.ClaimedJob job = diagnosisJobStateService.claim(jobId);
    if (job == null) {
      return;
    }
    try {
      diagnosisService.createFromAi(job.reportId(), job.requestId());
      diagnosisJobStateService.complete(jobId);
    } catch (RuntimeException exception) {
      diagnosisJobStateService.fail(jobId);
      log.warn("Diagnosis job failed job_id={}", jobId, exception);
    }
  }
}
