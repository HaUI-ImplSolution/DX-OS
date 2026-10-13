// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseView;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowDecisionHandler;
import HaUI.ImplSolution.dxos.lab.model.Device;
import HaUI.ImplSolution.dxos.lab.model.DeviceLoan;
import HaUI.ImplSolution.dxos.lab.model.DeviceStatus;
import HaUI.ImplSolution.dxos.lab.model.LoanStatus;
import HaUI.ImplSolution.dxos.lab.model.ReturnCondition;
import HaUI.ImplSolution.dxos.lab.repository.DeviceLoanRepository;
import HaUI.ImplSolution.dxos.lab.repository.DeviceRepository;
import java.util.List;
import org.springframework.stereotype.Component;

// Mirrors every step of a DEVICE_LOAN case onto the loan and moves the device between available,
// on loan and under repair in the same transaction.
@Component
public class DeviceLoanDecisionHandler implements WorkflowDecisionHandler {

  private final DeviceLoanRepository loanRepository;
  private final DeviceRepository deviceRepository;
  private final LabNotifier notifier;

  public DeviceLoanDecisionHandler(DeviceLoanRepository loanRepository,
      DeviceRepository deviceRepository, LabNotifier notifier) {
    this.loanRepository = loanRepository;
    this.deviceRepository = deviceRepository;
    this.notifier = notifier;
  }

  @Override
  public String caseType() {
    return LabCaseTypes.DEVICE_LOAN;
  }

  @Override
  public void onDecision(CaseView workflowCase, String target, String reason) {
    DeviceLoan loan = loanRepository.findById(workflowCase.entityId()).orElseThrow(
        () -> new ResourceNotFoundException("Device loan not found: " + workflowCase.entityId()));
    Device device = deviceRepository.findByIdForUpdate(loan.getDevice().getId())
        .orElseThrow(() -> new ResourceNotFoundException("Device not found"));
    switch (target) {
      case LoanStatus.APPROVED -> {
        requireFree(device, loan);
        device.setStatus(DeviceStatus.ON_LOAN);
        notifier.tell(loan.getBorrowerId(), LabNotificationTypes.LOAN_APPROVED,
            "Phiếu mượn được duyệt", "Bạn có thể nhận thiết bị %s, hạn trả %s."
                .formatted(device.getAssetCode(), loan.getDueDate()),
            LabCaseTypes.DEVICE_LOAN_ENTITY, loan.getId());
      }
      case LoanStatus.REJECTED -> {
        loan.setRejectionReason(reason);
        notifier.tell(loan.getBorrowerId(), LabNotificationTypes.LOAN_REJECTED,
            "Phiếu mượn bị từ chối", "Phiếu mượn thiết bị %s bị từ chối. Lý do: %s"
                .formatted(device.getAssetCode(), reason),
            LabCaseTypes.DEVICE_LOAN_ENTITY, loan.getId());
      }
      case LoanStatus.RETURNED -> device.setStatus(
          loan.getReturnCondition() == ReturnCondition.DAMAGED ? DeviceStatus.UNDER_REPAIR
              : DeviceStatus.AVAILABLE);
      default -> {
      }
    }
    loan.setStatus(target);
    deviceRepository.save(device);
    loanRepository.save(loan);
  }

  // The device may have been taken or sent to repair since the loan was requested.
  private void requireFree(Device device, DeviceLoan loan) {
    boolean held =
        loanRepository.existsByDevice_IdAndStatusIn(device.getId(), List.of(LoanStatus.APPROVED));
    if (device.getStatus() != DeviceStatus.AVAILABLE || held) {
      throw new PokaYokeViolationException("DEVICE_NOT_AVAILABLE",
          "Thiết bị %s hiện không sẵn sàng (%s)".formatted(device.getAssetCode(),
              device.getStatus()));
    }
  }
}
