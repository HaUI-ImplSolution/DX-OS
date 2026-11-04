// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.users;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.engine.users.UserAdminService.CreatedUser;
import HaUI.ImplSolution.dxos.engine.users.UserAdminService.NewUser;
import HaUI.ImplSolution.dxos.engine.users.UserAdminService.Options;
import HaUI.ImplSolution.dxos.engine.users.UserAdminService.UserDetail;
import HaUI.ImplSolution.dxos.engine.users.UserAdminService.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users",
    description = "See and change who holds the roles and units of the models, and keep accounts; ADMIN only")
public class UserAdminController {

  private final UserAdminService service;

  public UserAdminController(UserAdminService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "A page of the accounts, found by name, username or email")
  public ApiResponse<PageResponse<UserView>> list(@RequestParam(required = false) String search,
      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
    return ApiResponse.success(service.list(search, page, size));
  }

  @GetMapping("/options")
  @Operation(summary = "The roles and units that running models declare",
      description = "These are the only roles and units the other endpoints give or take.")
  public ApiResponse<Options> options() {
    return ApiResponse.success(service.options());
  }

  @GetMapping("/{id}")
  @Operation(summary = "One account with the roles it holds in each model and its units",
      description = "A role change reaches a user's token at the next sign-in or refresh. 404 when"
          + " the account does not exist.")
  public ApiResponse<UserDetail> get(@PathVariable String id) {
    return ApiResponse.success(service.get(id));
  }

  @PutMapping("/{id}/roles/{model}/{role}")
  @Operation(summary = "Give a role of a model",
      description = "404 when the account, the model or the role does not exist. The roles of the"
          + " platform are not reachable here.")
  public ApiResponse<Void> grantRole(@PathVariable String id, @PathVariable String model,
      @PathVariable String role) {
    service.grantRole(id, model, role);
    return ApiResponse.success(null);
  }

  @DeleteMapping("/{id}/roles/{model}/{role}")
  @Operation(summary = "Take a role of a model",
      description = "Not holding the role changes nothing; 404 when the account or the role does"
          + " not exist.")
  public ApiResponse<Void> revokeRole(@PathVariable String id, @PathVariable String model,
      @PathVariable String role) {
    service.revokeRole(id, model, role);
    return ApiResponse.success(null);
  }

  @PutMapping("/{id}/units/{unit}")
  @Operation(summary = "Add the account to a unit declared by a running model",
      description = "404 when the account or the unit does not exist.")
  public ApiResponse<Void> addUnit(@PathVariable String id, @PathVariable String unit) {
    service.addUnit(id, unit);
    return ApiResponse.success(null);
  }

  @DeleteMapping("/{id}/units/{unit}")
  @Operation(summary = "Take the account out of a unit",
      description = "Not being in the unit changes nothing; 404 when the account or the unit does"
          + " not exist.")
  public ApiResponse<Void> removeUnit(@PathVariable String id, @PathVariable String unit) {
    service.removeUnit(id, unit);
    return ApiResponse.success(null);
  }

  @PostMapping
  @Operation(summary = "Create an account with a temporary password",
      description = "The password is generated when none is given. It is returned once and must be"
          + " changed at the first sign-in. 400 when the username or the full name is missing,"
          + " 409 when the username or the email is taken.")
  public ApiResponse<CreatedUser> create(@RequestBody NewUser request) {
    return ApiResponse.success(service.create(request));
  }

  @PutMapping("/{id}/enabled")
  @Operation(summary = "Lock or unlock an account",
      description = "Body: {\"enabled\": true|false}. 409 when an administrator locks their own"
          + " account.")
  public ApiResponse<Void> setEnabled(@PathVariable String id,
      @RequestBody Map<String, Boolean> body) {
    service.setEnabled(id, Boolean.TRUE.equals(body.get("enabled")));
    return ApiResponse.success(null);
  }

  @PostMapping("/{id}/password")
  @Operation(summary = "Set a new temporary password",
      description = "The password is returned once and must be changed at the next sign-in.")
  public ApiResponse<Map<String, String>> resetPassword(@PathVariable String id) {
    return ApiResponse.success(Map.of("temporaryPassword", service.resetPassword(id)));
  }
}
