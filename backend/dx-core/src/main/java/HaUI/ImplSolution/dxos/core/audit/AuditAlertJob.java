// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.audit;

import HaUI.ImplSolution.dxos.core.api.audit.AuditOutcome;
import HaUI.ImplSolution.dxos.core.api.notification.ChatWebhook;
import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Tells the administrators' chat channel when one client address was refused many times in a short
// while, at most once per address in the repeat interval.
@Component
public class AuditAlertJob {

  private static final Logger log = LoggerFactory.getLogger(AuditAlertJob.class);
  private static final String CHANNEL = "admin";

  private final AuditLogRepository repository;
  private final ChatWebhook chatWebhook;
  private final JobLock jobLock;
  private final Clock clock;
  private final Duration window;
  private final long threshold;
  private final Duration repeat;
  private final Map<String, Instant> alerted = new HashMap<>();

  public AuditAlertJob(AuditLogRepository repository, ChatWebhook chatWebhook, JobLock jobLock,
      Clock clock, @Value("${audit.alert.window-minutes:10}") int windowMinutes,
      @Value("${audit.alert.threshold:10}") long threshold,
      @Value("${audit.alert.repeat-minutes:60}") int repeatMinutes) {
    this.repository = repository;
    this.chatWebhook = chatWebhook;
    this.jobLock = jobLock;
    this.clock = clock;
    this.window = Duration.ofMinutes(windowMinutes);
    this.threshold = threshold;
    this.repeat = Duration.ofMinutes(repeatMinutes);
  }

  @Scheduled(cron = "${audit.alert.cron:0 */5 * * * *}", zone = BusinessTime.ZONE_ID)
  public void run() {
    try {
      jobLock.runIfFree("audit-alert", this::check);
    } catch (Exception e) {
      log.error("Audit alert job failed", e);
    }
  }

  void check() {
    if (!chatWebhook.isEnabled(CHANNEL)) {
      return;
    }
    Instant now = Instant.now(clock);
    alerted.values().removeIf(sentAt -> sentAt.plus(repeat).isBefore(now));
    for (AuditLogRepository.DeniedSource source : repository.countBySource(AuditOutcome.DENIED,
        now.minus(window), threshold)) {
      if (alerted.putIfAbsent(source.getIp(), now) == null) {
        chatWebhook.send(CHANNEL, Map.of("text",
            ":warning: %d lần truy cập bị từ chối từ địa chỉ %s trong %d phút qua. Xem GET /api/v1/audit-logs?outcome=DENIED."
                .formatted(source.getTotal(), source.getIp(), window.toMinutes())));
      }
    }
  }
}
