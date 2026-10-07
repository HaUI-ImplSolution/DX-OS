package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowCase;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowDecisionHandler;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowStatus;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.ApprovalStatus;
import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.NotificationType;
import HaUI.ImplSolution.DX_Farm.repository.CropSeasonRepository;
import org.springframework.stereotype.Component;

// Mirrors the decision on a CROP_SEASON_APPROVAL case onto the crop season.
@Component
public class CropSeasonApprovalHandler implements WorkflowDecisionHandler {

  private final CropSeasonRepository cropSeasonRepository;
  private final NotificationService notificationService;

  public CropSeasonApprovalHandler(CropSeasonRepository cropSeasonRepository,
      NotificationService notificationService) {
    this.cropSeasonRepository = cropSeasonRepository;
    this.notificationService = notificationService;
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
    boolean approved = target == WorkflowStatus.APPROVED;
    String label = season.getCode() != null ? season.getCode() : "#" + season.getId();
    notificationService.notifyHousehold(season.getParcel().getHousehold(),
        approved ? NotificationType.CROP_SEASON_APPROVED : NotificationType.CROP_SEASON_REJECTED,
        approved ? "Vụ mùa đã được duyệt" : "Vụ mùa bị từ chối",
        approved ? "Vụ %s đã được duyệt.".formatted(label)
            : "Vụ %s bị từ chối. Lý do: %s".formatted(label, reason),
        "CROP_SEASON", String.valueOf(season.getId()));
  }
}
