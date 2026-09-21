// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowEventResponse;
import java.util.List;

// One request with its timeline, oldest event first.
// @formatter:off
public record ApprovalRequestDetailResponse(
  ApprovalRequestResponse request,

  List<WorkflowEventResponse> events
){}
