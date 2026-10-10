// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.workflow;

import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowActor;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowCaseResponse;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowEventResponse;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowStatus;
import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow-cases")
@Tag(name = "Workflow", description = "Pending work queue, assignees and case history")
@PreAuthorize("@currentUserService.isStaff()")
public class WorkflowController {

  private final WorkflowService workflowService;

  public WorkflowController(WorkflowService workflowService) {
    this.workflowService = workflowService;
  }

  @GetMapping
  @Operation(summary = "List workflow cases, oldest first",
      description = "Staff only. Filter by caseType (e.g. DIAGNOSIS_APPROVAL), status"
          + " and assignedTo (a user id, or `me`). size is capped at 100.")
  public ApiResponse<PageResponse<WorkflowCaseResponse>> list(
      @RequestParam(required = false) String caseType,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String assignedTo, @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal Jwt jwt) {
    String assignee = "me".equals(assignedTo) ? jwt.getSubject() : assignedTo;
    return ApiResponse.success(
        workflowService.list(caseType, status, assignee, page, size).map(WorkflowCaseResponse::of));
  }

  @PatchMapping("/{id}/assign")
  @Operation(summary = "Take a pending case (assignee = logged-in user)",
      description = "Staff only. 409 if the case is not pending, or is already"
          + " assigned to someone else (an ADMIN may take it over).")
  public ApiResponse<WorkflowCaseResponse> assign(@PathVariable Long id,
      @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
    boolean admin = authentication.getAuthorities().stream()
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    return ApiResponse.success("Case assigned",
        WorkflowCaseResponse.of(workflowService.assign(id, actorOf(jwt), admin)));
  }

  @PatchMapping("/{id}/approve")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Approve a pending case and apply it to the entity it tracks",
      description = "ADMIN only. 409 if the case is not pending, or its type is decided through"
          + " its own endpoint (diagnoses).")
  public ApiResponse<WorkflowCaseResponse> approve(@PathVariable Long id,
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success("Case approved", WorkflowCaseResponse
        .of(workflowService.decide(id, WorkflowStatus.APPROVED, actorOf(jwt), null)));
  }

  @PatchMapping("/{id}/reject")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Reject a pending case; the requester sees the reason and may resubmit",
      description = "ADMIN only. 400 without a reason. 409 if the case is not pending, or its"
          + " type is decided through its own endpoint (diagnoses).")
  public ApiResponse<WorkflowCaseResponse> reject(@PathVariable Long id,
      @Valid @RequestBody WorkflowDecisionRequest request, @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success("Case rejected", WorkflowCaseResponse
        .of(workflowService.decide(id, WorkflowStatus.REJECTED, actorOf(jwt), request.reason())));
  }

  @GetMapping("/{id}/events")
  @Operation(summary = "Timeline of a case, oldest first",
      description = "Staff only. Events are never modified or removed.")
  public ApiResponse<List<WorkflowEventResponse>> events(@PathVariable Long id) {
    return ApiResponse.success(workflowService.events(id));
  }

  private static WorkflowActor actorOf(Jwt jwt) {
    String name = jwt.getClaimAsString("name") != null ? jwt.getClaimAsString("name")
        : jwt.getClaimAsString("preferred_username");
    return new WorkflowActor(jwt.getSubject(), name);
  }
}
