package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowCase;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowDecisionHandler;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowStatus;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
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

  public ParcelEditApprovalHandler(ParcelEditRequestRepository editRequestRepository,
      ParcelRepository parcelRepository) {
    this.editRequestRepository = editRequestRepository;
    this.parcelRepository = parcelRepository;
  }

  @Override
  public String caseType() {
    return ApprovalCaseTypes.PARCEL_EDIT_REQUEST;
  }

  @Override
  public void onDecision(WorkflowCase workflowCase, WorkflowStatus target, String reason) {
    if (target != WorkflowStatus.APPROVED) {
      return;
    }
    ParcelEditRequest edit = editRequestRepository.findById(workflowCase.getEntityId())
        .orElseThrow(() -> new ResourceNotFoundException(
            "Parcel edit request not found: " + workflowCase.getEntityId()));
    Parcel parcel = edit.getParcel();
    parcel.setAreaHectares(edit.getAreaHectares());
    parcel.setLocation(edit.getLocation());
    parcel.setLatitude(edit.getLatitude());
    parcel.setLongitude(edit.getLongitude());
    parcel.setGpsAccuracyMeters(edit.getGpsAccuracyMeters());
    parcel.setLocationMissing(false);
    parcelRepository.save(parcel);
  }
}
