// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.workflow.CaseView;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowDecisionHandler;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowStatus;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.farm.model.ApprovalStatus;
import HaUI.ImplSolution.dxos.farm.model.NotificationType;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import HaUI.ImplSolution.dxos.farm.repository.ParcelRepository;
import org.springframework.stereotype.Component;

// Mirrors the decision on a PARCEL_APPROVAL case onto the parcel.
@Component
public class ParcelApprovalHandler implements WorkflowDecisionHandler {

  private final ParcelRepository parcelRepository;
  private final NotificationService notificationService;

  public ParcelApprovalHandler(ParcelRepository parcelRepository,
      NotificationService notificationService) {
    this.parcelRepository = parcelRepository;
    this.notificationService = notificationService;
  }

  @Override
  public String caseType() {
    return ApprovalCaseTypes.PARCEL_APPROVAL;
  }

  @Override
  public void onDecision(CaseView workflowCase, String target, String reason) {
    Parcel parcel = parcelRepository.findById(workflowCase.entityId()).orElseThrow(
        () -> new ResourceNotFoundException("Parcel not found: " + workflowCase.entityId()));
    if (WorkflowStatus.APPROVED.equals(target)) {
      parcel.setApprovalStatus(ApprovalStatus.APPROVED);
      parcel.setRejectionReason(null);
    } else {
      parcel.setApprovalStatus(ApprovalStatus.REJECTED);
      parcel.setRejectionReason(reason);
    }
    parcelRepository.save(parcel);
    boolean approved = WorkflowStatus.APPROVED.equals(target);
    notificationService.notifyHousehold(parcel.getHouseholdId(),
        approved ? NotificationType.PARCEL_APPROVED : NotificationType.PARCEL_REJECTED,
        approved ? "Thửa ruộng đã được duyệt" : "Thửa ruộng bị từ chối",
        approved ? "Thửa %s đã được duyệt, bạn có thể tạo vụ mùa.".formatted(parcel.getCode())
            : "Thửa %s bị từ chối. Lý do: %s".formatted(parcel.getCode(), reason),
        "PARCEL", parcel.getCode());
  }
}
