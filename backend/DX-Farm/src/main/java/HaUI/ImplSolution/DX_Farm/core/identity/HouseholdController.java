// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.core.identity;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdCreateRequest;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdCreateResponse;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdLinkRequest;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdResponse;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdService;
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

@RestController
@RequestMapping("/api/v1/households")
@Tag(name = "Household", description = "Nông hộ và tài khoản đăng nhập")
public class HouseholdController {

  private final HouseholdService householdService;

  public HouseholdController(HouseholdService householdService) {
    this.householdService = householdService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
  @Operation(summary = "List farm households", description = "ENGINEER or ADMIN only.")
  public ApiResponse<List<HouseholdResponse>> listAll() {
    return ApiResponse.success(householdService.listAll());
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
    return ApiResponse.success("Household created", householdService.create(request));
  }

  @PatchMapping("/{code}/account")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Link an existing Keycloak account to a household without one",
      description = "ADMIN only. Name the account by keycloakUserId or by username (exactly one, 400"
          + " otherwise). 409 if the household already has an account or the account is linked"
          + " elsewhere; 404 if the household or Keycloak user does not exist.")
  public ApiResponse<HouseholdResponse> linkAccount(@PathVariable String code,
      @Valid @RequestBody HouseholdLinkRequest request) {
    return ApiResponse.success("Account linked", householdService.linkAccount(code, request));
  }
}
