// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.workflow.CaseView;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowDecisionHandler;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowStatus;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.farm.model.NotificationType;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import HaUI.ImplSolution.dxos.farm.model.ParcelEditRequest;
import HaUI.ImplSolution.dxos.farm.repository.ParcelEditRequestRepository;
import HaUI.ImplSolution.dxos.farm.repository.ParcelRepository;
import org.springframework.stereotype.Component;

// An approved edit request overwrites the parcel; a rejected one leaves it untouched.
@Component
public class ParcelEditApprovalHandler implements WorkflowDecisionHandler {

  private final ParcelEditRequestRepository editRequestRepository;
  private final ParcelRepository parcelRepository;
  private final NotificationService notificationService;

  public ParcelEditApprovalHandler(ParcelEditRequestRepository editRequestRepository,
      ParcelRepository parcelRepository, NotificationService notificationService) {
    this.editRequestRepository = editRequestRepository;
    this.parcelRepository = parcelRepository;
    this.notificationService = notificationService;
  }

  @Override
  public String caseType() {
    return ApprovalCaseTypes.PARCEL_EDIT_REQUEST;
  }

  @Override
  public void onDecision(CaseView workflowCase, WorkflowStatus target, String reason) {
    ParcelEditRequest edit = editRequestRepository.findById(workflowCase.entityId())
        .orElseThrow(() -> new ResourceNotFoundException(
            "Parcel edit request not found: " + workflowCase.entityId()));
    Parcel parcel = edit.getParcel();
    boolean approved = target == WorkflowStatus.APPROVED;
    if (approved) {
      parcel.setAreaHectares(edit.getAreaHectares());
      parcel.setLocation(edit.getLocation());
      parcel.setLatitude(edit.getLatitude());
      parcel.setLongitude(edit.getLongitude());
      parcel.setGpsAccuracyMeters(edit.getGpsAccuracyMeters());
      parcel.setLocationMissing(false);
      parcelRepository.save(parcel);
    }
    notificationService.notifyHousehold(parcel.getHouseholdId(),
        approved ? NotificationType.PARCEL_EDIT_APPROVED : NotificationType.PARCEL_EDIT_REJECTED,
        approved ? "Yêu cầu sửa thửa đã được duyệt" : "Yêu cầu sửa thửa bị từ chối",
        approved ? "Thông tin thửa %s đã được cập nhật.".formatted(parcel.getCode())
            : "Yêu cầu sửa thửa %s bị từ chối. Lý do: %s".formatted(parcel.getCode(), reason),
        "PARCEL", parcel.getCode());
  }
}
