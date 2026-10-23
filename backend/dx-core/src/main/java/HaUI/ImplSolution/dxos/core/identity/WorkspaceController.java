// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import HaUI.ImplSolution.dxos.core.api.audit.AuditEvent;
import HaUI.ImplSolution.dxos.core.api.audit.AuditRecorder;
import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceAdministration;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceCreateCommand;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceCreated;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceDirectory;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceMemberRole;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceMemberView;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
@Tag(name = "Workspace", description = "Workspaces and the accounts that belong to them")
public class WorkspaceController {

  private final WorkspaceDirectory workspaceDirectory;
  private final WorkspaceAdministration workspaceAdministration;
  private final AuditRecorder auditRecorder;

  public WorkspaceController(WorkspaceDirectory workspaceDirectory,
      WorkspaceAdministration workspaceAdministration, AuditRecorder auditRecorder) {
    this.workspaceDirectory = workspaceDirectory;
    this.workspaceAdministration = workspaceAdministration;
    this.auditRecorder = auditRecorder;
  }

  @GetMapping
  @PreAuthorize("@currentUserService.isStaff()")
  @Operation(summary = "List workspaces",
      description = "Staff only. Filter by type; every type when omitted.")
  public ApiResponse<List<WorkspaceResponse>> list(@RequestParam(required = false) String type) {
    return ApiResponse.success(
        workspaceDirectory.findAll(type).stream().map(WorkspaceController::toResponse).toList());
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a workspace, optionally with its owner account",
      description = "ADMIN only. With username and accountRole the Keycloak user is created"
          + " (temporary password, changed at first login) and becomes the owner; the temporary"
          + " password is returned once. 400 for an unknown type; 409 if the username or email"
          + " exists; 502 if Keycloak is unavailable (nothing is created).")
  public ApiResponse<WorkspaceCreateResponse> create(
      @Valid @RequestBody WorkspaceCreateRequest request) {
    WorkspaceCreated created =
        workspaceAdministration.create(new WorkspaceCreateCommand(request.type(), request.name(),
            request.phone(), request.address(), request.attributes(), request.username(),
            request.email(), request.accountRole()));
    auditRecorder
        .record(AuditEvent.success("WORKSPACE_CREATED", "WORKSPACE", created.workspace().code(),
            java.util.Map.of("type", request.type(), "withAccount", created.username() != null)));
    return ApiResponse.success("Workspace created", new WorkspaceCreateResponse(
        toResponse(created.workspace()), created.username(), created.temporaryPassword()));
  }

  @PatchMapping("/{code}/account")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Link an existing Keycloak account as owner of a workspace without one",
      description = "ADMIN only. Name the account by keycloakUserId or by username (exactly one,"
          + " 400 otherwise). 409 if the workspace already has an owner or the account owns"
          + " another workspace of the same type; 404 if the workspace or Keycloak user does not"
          + " exist.")
  public ApiResponse<WorkspaceResponse> linkAccount(@PathVariable String code,
      @Valid @RequestBody WorkspaceLinkRequest request) {
    WorkspaceView linked =
        workspaceAdministration.linkOwner(code, request.keycloakUserId(), request.username());
    auditRecorder.record(AuditEvent.success("WORKSPACE_LINKED", "WORKSPACE", code));
    return ApiResponse.success("Account linked", toResponse(linked));
  }

  @GetMapping("/{code}/members")
  @PreAuthorize("@currentUserService.isStaff()")
  @Operation(summary = "List the members of a workspace", description = "Staff only.")
  public ApiResponse<List<WorkspaceMemberResponse>> members(@PathVariable String code) {
    WorkspaceView workspace = workspaceDirectory.findByCode(code)
        .orElseThrow(() -> new ResourceNotFoundException("Workspace not found: " + code));
    return ApiResponse.success(workspaceDirectory.members(workspace.id()).stream()
        .map(WorkspaceController::toMemberResponse).toList());
  }

  @PostMapping("/{code}/members")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add an existing account to a workspace",
      description = "ADMIN only. Name the account by keycloakUserId or by username (exactly one,"
          + " 400 otherwise); role defaults to MEMBER. 404 if the workspace or Keycloak user does"
          + " not exist; 409 if the account is the owner.")
  public ApiResponse<WorkspaceMemberResponse> addMember(@PathVariable String code,
      @Valid @RequestBody WorkspaceMemberRequest request) {
    WorkspaceMemberRole role = request.role() != null ? request.role() : WorkspaceMemberRole.MEMBER;
    WorkspaceMemberView member =
        workspaceAdministration.addMember(code, request.keycloakUserId(), request.username(), role);
    auditRecorder.record(AuditEvent.success("MEMBER_ADDED", "WORKSPACE", code,
        java.util.Map.of("userId", member.userId(), "role", member.role().name())));
    return ApiResponse.success("Member added", toMemberResponse(member));
  }

  static WorkspaceResponse toResponse(WorkspaceView view) {
    return new WorkspaceResponse(view.id(), view.code(), view.type(), view.name(), view.phone(),
        view.address(), view.ownerUserId() != null, view.attributes(), view.createdAt());
  }

  private static WorkspaceMemberResponse toMemberResponse(WorkspaceMemberView member) {
    return new WorkspaceMemberResponse(member.userId(), member.role(), member.createdAt());
  }
}
