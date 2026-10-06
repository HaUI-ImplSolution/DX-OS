package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowCase;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowDecisionHandler;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowStatus;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.ApprovalStatus;
import HaUI.ImplSolution.DX_Farm.model.Parcel;
import HaUI.ImplSolution.DX_Farm.repository.ParcelRepository;
import org.springframework.stereotype.Component;

// Mirrors the decision on a PARCEL_APPROVAL case onto the parcel.
@Component
public class ParcelApprovalHandler implements WorkflowDecisionHandler {

  private final ParcelRepository parcelRepository;

  public ParcelApprovalHandler(ParcelRepository parcelRepository) {
    this.parcelRepository = parcelRepository;
  }

  @Override
  public String caseType() {
    return ApprovalCaseTypes.PARCEL_APPROVAL;
  }

  @Override
  public void onDecision(WorkflowCase workflowCase, WorkflowStatus target, String reason) {
    Parcel parcel = parcelRepository.findById(workflowCase.getEntityId()).orElseThrow(
        () -> new ResourceNotFoundException("Parcel not found: " + workflowCase.getEntityId()));
    if (target == WorkflowStatus.APPROVED) {
      parcel.setApprovalStatus(ApprovalStatus.APPROVED);
      parcel.setRejectionReason(null);
    } else {
      parcel.setApprovalStatus(ApprovalStatus.REJECTED);
      parcel.setRejectionReason(reason);
    }
    parcelRepository.save(parcel);
  }
}
