// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.controller;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceAdministration;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceCreateCommand;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceCreated;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceDirectory;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;
import HaUI.ImplSolution.dxos.farm.FarmAppModule;
import HaUI.ImplSolution.dxos.farm.dto.HouseholdCreateRequest;
import HaUI.ImplSolution.dxos.farm.dto.HouseholdCreateResponse;
import HaUI.ImplSolution.dxos.farm.dto.HouseholdLinkRequest;
import HaUI.ImplSolution.dxos.farm.dto.HouseholdResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Farm households are workspaces of type HOUSEHOLD whose owner account holds the FARMER role.
@RestController
@RequestMapping("/api/v1/households")
@Tag(name = "Household", description = "Nông hộ và tài khoản đăng nhập")
public class HouseholdController {

  private static final String FARMER_ROLE = "FARMER";

  private final WorkspaceDirectory workspaceDirectory;
  private final WorkspaceAdministration workspaceAdministration;

  public HouseholdController(WorkspaceDirectory workspaceDirectory,
      WorkspaceAdministration workspaceAdministration) {
    this.workspaceDirectory = workspaceDirectory;
    this.workspaceAdministration = workspaceAdministration;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
  @Operation(summary = "List farm households", description = "ENGINEER or ADMIN only.")
  public ApiResponse<List<HouseholdResponse>> listAll() {
    return ApiResponse.success(workspaceDirectory.findAll(FarmAppModule.HOUSEHOLD).stream()
        .map(HouseholdController::toResponse).toList());
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a household together with its FARMER account",
      description = "ADMIN only. Creates the Keycloak user (temporary password, changed at first"
          + " login) and links it to the new household. The temporary password is returned once."
          + " 409 if the username or email exists; 502 if Keycloak is unavailable (nothing is"
          + " created).")
  public ApiResponse<HouseholdCreateResponse> create(
      @Valid @RequestBody HouseholdCreateRequest request) {
    WorkspaceCreated created = workspaceAdministration.create(
        new WorkspaceCreateCommand(FarmAppModule.HOUSEHOLD, request.fullName(), request.phone(),
            request.address(), null, request.username(), request.email(), FARMER_ROLE));
    return ApiResponse.success("Household created", new HouseholdCreateResponse(
        toResponse(created.workspace()), created.username(), created.temporaryPassword()));
  }

  @PatchMapping("/{code}/account")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Link an existing Keycloak account to a household without one",
      description = "ADMIN only. Name the account by keycloakUserId or by username (exactly one, 400"
          + " otherwise). 409 if the household already has an account or the account is linked"
          + " elsewhere; 404 if the household or Keycloak user does not exist.")
  public ApiResponse<HouseholdResponse> linkAccount(@PathVariable String code,
      @Valid @RequestBody HouseholdLinkRequest request) {
    workspaceDirectory.findByCode(code).filter(w -> FarmAppModule.HOUSEHOLD.equals(w.type()))
        .orElseThrow(() -> new ResourceNotFoundException("Household not found: " + code));
    return ApiResponse.success("Account linked", toResponse(
        workspaceAdministration.linkOwner(code, request.keycloakUserId(), request.username())));
  }

  private static HouseholdResponse toResponse(WorkspaceView workspace) {
    return new HouseholdResponse(workspace.id(), workspace.code(), workspace.name(),
        workspace.phone(), workspace.address(), workspace.ownerUserId() != null,
        workspace.createdAt());
  }
}
