// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Runs the reminder for waiting spraying reports on its schedule (Asia/Ho_Chi_Minh), on one
// instance at a time.
@Component
public class TreatmentConfirmationReminderScheduler {

  private static final Logger log =
      LoggerFactory.getLogger(TreatmentConfirmationReminderScheduler.class);

  private final TreatmentConfirmationReminderService reminderService;
  private final JobLock jobLock;

  public TreatmentConfirmationReminderScheduler(
      TreatmentConfirmationReminderService reminderService, JobLock jobLock) {
    this.reminderService = reminderService;
    this.jobLock = jobLock;
  }

  @Scheduled(cron = "${app.treatment-confirmation-reminder-cron:0 0 8 * * *}",
      zone = BusinessTime.ZONE_ID)
  public void run() {
    try {
      jobLock.runIfFree("farm-treatment-confirmation-reminder", () -> log
          .info("Treatment confirmation reminders posted: {}", reminderService.remindOverdue()));
    } catch (Exception e) {
      log.error("Treatment confirmation reminder job failed", e);
    }
  }
}
