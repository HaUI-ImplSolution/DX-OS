// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowCase;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowDecisionHandler;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowStatus;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.NotificationType;
import HaUI.ImplSolution.DX_Farm.model.Parcel;
import HaUI.ImplSolution.DX_Farm.model.ParcelEditRequest;
import HaUI.ImplSolution.DX_Farm.repository.ParcelEditRequestRepository;
import HaUI.ImplSolution.DX_Farm.repository.ParcelRepository;
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
  public void onDecision(WorkflowCase workflowCase, WorkflowStatus target, String reason) {
    ParcelEditRequest edit = editRequestRepository.findById(workflowCase.getEntityId())
        .orElseThrow(() -> new ResourceNotFoundException(
            "Parcel edit request not found: " + workflowCase.getEntityId()));
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
    notificationService.notifyHousehold(parcel.getHousehold(),
        approved ? NotificationType.PARCEL_EDIT_APPROVED : NotificationType.PARCEL_EDIT_REJECTED,
        approved ? "Yêu cầu sửa thửa đã được duyệt" : "Yêu cầu sửa thửa bị từ chối",
        approved ? "Thông tin thửa %s đã được cập nhật.".formatted(parcel.getCode())
            : "Yêu cầu sửa thửa %s bị từ chối. Lý do: %s".formatted(parcel.getCode(), reason),
        "PARCEL", parcel.getCode());
  }
}
