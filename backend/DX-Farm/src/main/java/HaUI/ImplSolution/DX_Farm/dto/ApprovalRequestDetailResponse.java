package HaUI.ImplSolution.DX_Farm.dto;

import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowEventResponse;
import java.util.List;

// One request with its timeline, oldest event first.
// @formatter:off
public record ApprovalRequestDetailResponse(
  ApprovalRequestResponse request,

  List<WorkflowEventResponse> events
){}
