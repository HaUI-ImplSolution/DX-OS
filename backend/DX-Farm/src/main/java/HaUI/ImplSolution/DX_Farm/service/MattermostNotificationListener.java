package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.event.CropSeasonCreatedEvent;
import HaUI.ImplSolution.DX_Farm.event.DiagnosisPendingEvent;
import HaUI.ImplSolution.DX_Farm.event.DiseaseReportCreatedEvent;
import HaUI.ImplSolution.DX_Farm.event.ParcelCreatedEvent;
import HaUI.ImplSolution.DX_Farm.event.TreatmentReportedEvent;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Sends a Mattermost notification after the publishing transaction commits. Runs on a separate
// executor and never propagates an exception back to the business flow.
@Component
public class MattermostNotificationListener {

  private static final Logger log = LoggerFactory.getLogger(MattermostNotificationListener.class);

  private final MattermostWebhookClient webhookClient;
  private final MattermostMessageBuilder messageBuilder;

  public MattermostNotificationListener(MattermostWebhookClient webhookClient,
      MattermostMessageBuilder messageBuilder) {
    this.webhookClient = webhookClient;
    this.messageBuilder = messageBuilder;
  }

  @Async("mattermostNotifyExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReportCreated(DiseaseReportCreatedEvent event) {
    dispatch(MattermostChannel.ENGINEER, () -> messageBuilder.reportCreated(event));
  }

  @Async("mattermostNotifyExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDiagnosisPending(DiagnosisPendingEvent event) {
    dispatch(MattermostChannel.ENGINEER, () -> messageBuilder.diagnosisPending(event));
  }

  @Async("mattermostNotifyExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTreatmentReported(TreatmentReportedEvent event) {
    dispatch(MattermostChannel.ENGINEER, () -> messageBuilder.treatmentReported(event));
  }

  @Async("mattermostNotifyExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onParcelCreated(ParcelCreatedEvent event) {
    dispatch(MattermostChannel.ADMIN, () -> messageBuilder.parcelCreated(event));
  }

  @Async("mattermostNotifyExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCropSeasonCreated(CropSeasonCreatedEvent event) {
    dispatch(MattermostChannel.ADMIN, () -> messageBuilder.cropSeasonCreated(event));
  }

  private void dispatch(MattermostChannel channel, Supplier<Map<String, Object>> payload) {
    if (!webhookClient.isEnabled(channel)) {
      return;
    }
    try {
      webhookClient.send(channel, payload.get());
    } catch (Exception e) {
      log.warn("Mattermost notification failed: {}", e.getClass().getSimpleName());
    }
  }
}
