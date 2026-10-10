// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

// Names the account either by its Keycloak id or by its username; exactly one is given.
// @formatter:off
public record WorkspaceLinkRequest(
  // Keycloak user id (token claim "sub")
  @Size(max = 255)
  String keycloakUserId,

  @Size(max = 255)
  String username
){
  @AssertTrue(message = "give either keycloakUserId or username")
  boolean isExactlyOneAccountGiven() {
    return (keycloakUserId == null || keycloakUserId.isBlank()) != (username == null || username.isBlank());
  }
}
