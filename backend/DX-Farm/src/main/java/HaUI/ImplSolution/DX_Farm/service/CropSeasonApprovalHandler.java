package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowCase;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowDecisionHandler;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowStatus;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.ApprovalStatus;
import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.repository.CropSeasonRepository;
import org.springframework.stereotype.Component;

// Mirrors the decision on a CROP_SEASON_APPROVAL case onto the crop season.
@Component
public class CropSeasonApprovalHandler implements WorkflowDecisionHandler {

  private final CropSeasonRepository cropSeasonRepository;

  public CropSeasonApprovalHandler(CropSeasonRepository cropSeasonRepository) {
    this.cropSeasonRepository = cropSeasonRepository;
  }

  @Override
  public String caseType() {
    return ApprovalCaseTypes.CROP_SEASON_APPROVAL;
  }

  @Override
  public void onDecision(WorkflowCase workflowCase, WorkflowStatus target, String reason) {
    CropSeason season = cropSeasonRepository.findById(workflowCase.getEntityId())
        .orElseThrow(() -> new ResourceNotFoundException(
            "Crop season not found: " + workflowCase.getEntityId()));
    if (target == WorkflowStatus.APPROVED) {
      season.setApprovalStatus(ApprovalStatus.APPROVED);
      season.setRejectionReason(null);
    } else {
      season.setApprovalStatus(ApprovalStatus.REJECTED);
      season.setRejectionReason(reason);
    }
    cropSeasonRepository.save(season);
  }
}
