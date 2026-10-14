// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Runs the SLA watch and the loan reminders on their schedules (Asia/Ho_Chi_Minh), on one
// instance at a time.
@Component
public class LabScheduler {

  private static final Logger log = LoggerFactory.getLogger(LabScheduler.class);

  private final TicketSlaService slaService;
  private final LoanReminderService reminderService;
  private final JobLock jobLock;

  public LabScheduler(TicketSlaService slaService, LoanReminderService reminderService,
      JobLock jobLock) {
    this.slaService = slaService;
    this.reminderService = reminderService;
    this.jobLock = jobLock;
  }

  @Scheduled(cron = "${lab.sla.check-cron:0 */5 * * * *}", zone = BusinessTime.ZONE_ID)
  public void checkSla() {
    try {
      jobLock.runIfFree("lab-sla-check", () -> {
        TicketSlaService.Result result = slaService.check();
        if (result.warned() > 0 || result.overdue() > 0) {
          log.info("SLA check: {} warned, {} marked overdue", result.warned(), result.overdue());
        }
      });
    } catch (Exception e) {
      log.error("SLA check failed", e);
    }
  }

  @Scheduled(cron = "${lab.reminder.loan-cron:0 0 7 * * *}", zone = BusinessTime.ZONE_ID)
  public void remindLoans() {
    try {
      jobLock.runIfFree("lab-loan-reminder", () -> {
        LoanReminderService.Result result = reminderService.remind();
        log.info("Loan reminders: {} due soon, {} overdue", result.dueSoon(), result.overdue());
      });
    } catch (Exception e) {
      log.error("Loan reminder job failed", e);
    }
  }
}
