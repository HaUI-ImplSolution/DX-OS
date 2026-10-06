package HaUI.ImplSolution.DX_Farm.core.workflow;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow-cases")
@Tag(name = "Workflow", description = "Pending work queue, assignees and case history")
@PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
public class WorkflowController {

  private final WorkflowService workflowService;

  public WorkflowController(WorkflowService workflowService) {
    this.workflowService = workflowService;
  }

  @GetMapping
  @Operation(summary = "List workflow cases, oldest first",
      description = "ENGINEER or ADMIN only. Filter by caseType (e.g. DIAGNOSIS_APPROVAL), status"
          + " and assignedTo (a user id, or `me`). size is capped at 100.")
  public ApiResponse<PageResponse<WorkflowCaseResponse>> list(
      @RequestParam(required = false) String caseType,
      @RequestParam(required = false) WorkflowStatus status,
      @RequestParam(required = false) String assignedTo, @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal Jwt jwt) {
    String assignee = "me".equals(assignedTo) ? jwt.getSubject() : assignedTo;
    return ApiResponse.success(workflowService.list(caseType, status, assignee, page, size));
  }

  @PatchMapping("/{id}/assign")
  @Operation(summary = "Take a pending case (assignee = logged-in user)",
      description = "ENGINEER or ADMIN only. 409 if the case is not pending, or is already"
          + " assigned to someone else (an ADMIN may take it over).")
  public ApiResponse<WorkflowCaseResponse> assign(@PathVariable Long id,
      @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
    String name = jwt.getClaimAsString("name") != null ? jwt.getClaimAsString("name")
        : jwt.getClaimAsString("preferred_username");
    boolean admin = authentication.getAuthorities().stream()
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    return ApiResponse.success("Case assigned",
        workflowService.assign(id, new WorkflowActor(jwt.getSubject(), name), admin));
  }

  @GetMapping("/{id}/events")
  @Operation(summary = "Timeline of a case, oldest first",
      description = "ENGINEER or ADMIN only. Events are never modified or removed.")
  public ApiResponse<List<WorkflowEventResponse>> events(@PathVariable Long id) {
    return ApiResponse.success(workflowService.events(id));
  }
}
