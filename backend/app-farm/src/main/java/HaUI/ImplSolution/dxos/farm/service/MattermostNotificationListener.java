// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.notification.ChatWebhook;
import HaUI.ImplSolution.dxos.farm.event.CropSeasonCreatedEvent;
import HaUI.ImplSolution.dxos.farm.event.DiagnosisDecidedEvent;
import HaUI.ImplSolution.dxos.farm.event.DiagnosisPendingEvent;
import HaUI.ImplSolution.dxos.farm.event.DiseaseReportCreatedEvent;
import HaUI.ImplSolution.dxos.farm.event.ParcelCreatedEvent;
import HaUI.ImplSolution.dxos.farm.event.TreatmentConfirmationOverdueEvent;
import HaUI.ImplSolution.dxos.farm.event.TreatmentDecidedEvent;
import HaUI.ImplSolution.dxos.farm.event.TreatmentReportedEvent;
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

  private final ChatWebhook webhookClient;
  private final MattermostMessageBuilder messageBuilder;

  public MattermostNotificationListener(ChatWebhook webhookClient,
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
  public void onTreatmentConfirmationOverdue(TreatmentConfirmationOverdueEvent event) {
    dispatch(MattermostChannel.ENGINEER, () -> messageBuilder.treatmentConfirmationOverdue(event));
  }

  @Async("mattermostNotifyExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDiagnosisDecided(DiagnosisDecidedEvent event) {
    dispatch(MattermostChannel.ENGINEER, () -> messageBuilder.diagnosisDecided(event));
  }

  @Async("mattermostNotifyExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTreatmentDecided(TreatmentDecidedEvent event) {
    dispatch(MattermostChannel.ENGINEER, () -> messageBuilder.treatmentDecided(event));
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
    if (!webhookClient.isEnabled(channel.key())) {
      return;
    }
    try {
      webhookClient.send(channel.key(), payload.get());
    } catch (Exception e) {
      log.warn("Mattermost notification failed: {}", e.getClass().getSimpleName());
    }
  }
}
