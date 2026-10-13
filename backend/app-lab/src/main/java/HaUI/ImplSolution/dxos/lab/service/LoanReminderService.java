// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.lab.model.DeviceLoan;
import HaUI.ImplSolution.dxos.lab.model.LoanStatus;
import HaUI.ImplSolution.dxos.lab.repository.DeviceLoanRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Reminds borrowers once a day: a device is due within the configured days, or is already late.
@Service
public class LoanReminderService {

  public record Result(int dueSoon, int overdue) {}

  private final DeviceLoanRepository loanRepository;
  private final LabNotifier notifier;
  private final Clock clock;
  private final int dueSoonDays;

  public LoanReminderService(DeviceLoanRepository loanRepository, LabNotifier notifier, Clock clock,
      @Value("${lab.reminder.loan-due-soon-days:1}") int dueSoonDays) {
    this.loanRepository = loanRepository;
    this.notifier = notifier;
    this.clock = clock;
    this.dueSoonDays = dueSoonDays;
  }

  @Transactional
  public Result remind() {
    LocalDate today = LocalDate.now(clock);
    int dueSoon = 0;
    int overdue = 0;
    for (DeviceLoan loan : loanRepository.findByStatusAndDueDateLessThanEqual(LoanStatus.APPROVED,
        today.plusDays(dueSoonDays))) {
      String asset = loan.getDevice().getAssetCode();
      if (loan.getDueDate().isBefore(today)) {
        if (notifier.tellOnce("loan:%d:overdue:%s".formatted(loan.getId(), today),
            loan.getBorrowerId(), LabNotificationTypes.LOAN_OVERDUE, "Quá hạn trả thiết bị",
            "Thiết bị %s đã quá hạn trả từ ngày %s, hãy trả lại sớm.".formatted(asset,
                loan.getDueDate()),
            LabCaseTypes.DEVICE_LOAN_ENTITY, loan.getId())) {
          overdue++;
        }
      } else if (notifier.tellOnce("loan:%d:due-soon:%s".formatted(loan.getId(), today),
          loan.getBorrowerId(), LabNotificationTypes.LOAN_DUE_SOON, "Sắp đến hạn trả thiết bị",
          "Thiết bị %s cần trả trước ngày %s.".formatted(asset, loan.getDueDate()),
          LabCaseTypes.DEVICE_LOAN_ENTITY, loan.getId())) {
        dueSoon++;
      }
    }
    return new Result(dueSoon, overdue);
  }
}
