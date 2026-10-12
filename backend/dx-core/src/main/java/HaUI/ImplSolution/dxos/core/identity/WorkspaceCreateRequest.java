// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

// @formatter:off
public record WorkspaceCreateRequest(
  // One of the workspace types registered by the running app
  @NotBlank
  @Size(max = 32)
  String type,

  @NotBlank
  @Size(max = 255)
  String name,

  @Size(max = 255)
  String phone,

  @Size(max = 255)
  String address,

  // Free-form properties of the app
  Map<String, String> attributes,

  // Set together with accountRole to create the owner account; omit for a workspace without one
  @Pattern(regexp = "[a-z0-9._-]{3,50}")
  String username,

  @Email
  @Size(max = 255)
  String email,

  // Realm role granted to the new account
  @Size(max = 64)
  String accountRole
){
  @AssertTrue(message = "name needs at least two words when an account is created")
  boolean isNameValidForAccount() {
    return username == null || username.isBlank() || (name != null && name.trim().contains(" "));
  }

  @AssertTrue(message = "accountRole is required with username, and only then")
  boolean isAccountRoleConsistent() {
    boolean hasUsername = username != null && !username.isBlank();
    boolean hasRole = accountRole != null && !accountRole.isBlank();
    return hasUsername == hasRole;
  }
}
