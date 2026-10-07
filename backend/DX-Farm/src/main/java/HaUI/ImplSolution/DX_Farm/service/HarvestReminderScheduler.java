package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.config.TimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Runs the harvest reminders every morning (Asia/Ho_Chi_Minh).
@Component
public class HarvestReminderScheduler {

  private static final Logger log = LoggerFactory.getLogger(HarvestReminderScheduler.class);

  private final HarvestReminderService harvestReminderService;

  public HarvestReminderScheduler(HarvestReminderService harvestReminderService) {
    this.harvestReminderService = harvestReminderService;
  }

  @Scheduled(cron = "${app.harvest-reminder-cron:0 0 7 * * *}", zone = TimeConfig.BUSINESS_ZONE_ID)
  public void run() {
    try {
      log.info("Harvest reminders created: {}", harvestReminderService.sendDueReminders());
    } catch (Exception e) {
      log.error("Harvest reminder job failed", e);
    }
  }
}
