// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowStatus;
import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.ApprovalRequestDetailResponse;
import HaUI.ImplSolution.DX_Farm.dto.ApprovalRequestResponse;
import HaUI.ImplSolution.DX_Farm.dto.PageResponse;
import HaUI.ImplSolution.DX_Farm.service.ApprovalRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/approval-requests")
@Tag(name = "ApprovalRequest", description = "Requests waiting for a decision, with their entity")
@PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
public class ApprovalRequestController {

  private final ApprovalRequestService approvalRequestService;

  public ApprovalRequestController(ApprovalRequestService approvalRequestService) {
    this.approvalRequestService = approvalRequestService;
  }

  @GetMapping
  @Operation(
      summary = "List requests with the parcel, season, diagnosis or spraying report behind each, oldest first",
      description = "ENGINEER or ADMIN only. Filter by caseType (PARCEL_APPROVAL,"
          + " PARCEL_EDIT_REQUEST, CROP_SEASON_APPROVAL, DIAGNOSIS_APPROVAL, TREATMENT_CONFIRMATION), status, householdCode,"
          + " assignedTo (a user id, or `me`), unassigned=true and the day range from/to"
          + " (yyyy-MM-dd, inclusive). size is capped at 100.")
  public ApiResponse<PageResponse<ApprovalRequestResponse>> list(
      @RequestParam(required = false) String caseType,
      @RequestParam(required = false) WorkflowStatus status,
      @RequestParam(required = false) String householdCode,
      @RequestParam(required = false) String assignedTo,
      @RequestParam(defaultValue = "false") boolean unassigned,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal Jwt jwt) {
    String assignee = "me".equals(assignedTo) ? jwt.getSubject() : assignedTo;
    return ApiResponse.success(approvalRequestService.list(caseType, status, householdCode,
        assignee, unassigned, from, to, page, size));
  }

  @GetMapping("/{caseId}")
  @Operation(summary = "One request with its timeline",
      description = "ENGINEER or ADMIN only. 404 for an unknown case id.")
  public ApiResponse<ApprovalRequestDetailResponse> detail(@PathVariable Long caseId) {
    return ApiResponse.success(approvalRequestService.detail(caseId));
  }
}
