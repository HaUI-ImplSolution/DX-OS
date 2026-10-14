// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Runs the harvest reminders every morning (Asia/Ho_Chi_Minh), on one instance at a time.
@Component
public class HarvestReminderScheduler {

  private static final Logger log = LoggerFactory.getLogger(HarvestReminderScheduler.class);

  private final HarvestReminderService harvestReminderService;
  private final JobLock jobLock;

  public HarvestReminderScheduler(HarvestReminderService harvestReminderService, JobLock jobLock) {
    this.harvestReminderService = harvestReminderService;
    this.jobLock = jobLock;
  }

  @Scheduled(cron = "${app.harvest-reminder-cron:0 0 7 * * *}", zone = BusinessTime.ZONE_ID)
  public void run() {
    try {
      jobLock.runIfFree("farm-harvest-reminder", () -> log.info("Harvest reminders created: {}",
          harvestReminderService.sendDueReminders()));
    } catch (Exception e) {
      log.error("Harvest reminder job failed", e);
    }
  }
}
