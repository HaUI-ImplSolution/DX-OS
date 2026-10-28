// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.notification;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Sends the chat messages in ChatOutbox again. One backend instance runs it at a time.
@Component
public class ChatOutboxJob {

  private static final Logger log = LoggerFactory.getLogger(ChatOutboxJob.class);
  private static final int BATCH = 50;
  private static final Duration KEEP_FAILED = Duration.ofDays(7);

  private final ChatOutbox outbox;
  private final MattermostWebhookClient webhook;
  private final JobLock jobLock;

  public ChatOutboxJob(ChatOutbox outbox, MattermostWebhookClient webhook, JobLock jobLock) {
    this.outbox = outbox;
    this.webhook = webhook;
    this.jobLock = jobLock;
  }

  @Scheduled(fixedDelayString = "${chat.outbox.interval-ms:60000}", initialDelay = 60000)
  public void run() {
    try {
      jobLock.runIfFree("chat-outbox", this::drain);
    } catch (Exception e) {
      log.error("Chat outbox job failed", e);
    }
  }

  void drain() {
    for (ChatOutbox.Entry entry : outbox.due(BATCH)) {
      String error = webhook.post(entry.channelKey(), entry.payload());
      if (error == null) {
        outbox.delete(entry.id());
      } else {
        outbox.failedAgain(entry, error);
      }
    }
    int purged = outbox.purgeFailed(KEEP_FAILED);
    if (purged > 0) {
      log.warn("{} chat messages gave up after {} attempts and were removed", purged,
          ChatOutbox.MAX_ATTEMPTS);
    }
  }
}
