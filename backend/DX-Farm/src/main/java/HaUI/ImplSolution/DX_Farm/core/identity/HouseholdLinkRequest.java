package HaUI.ImplSolution.DX_Farm.core.identity;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

// Names the account to link either by its Keycloak id or by its username; exactly one is given.
// @formatter:off
public record HouseholdLinkRequest(
  // Keycloak user id (token claim "sub") of the account to link to the household
  @Size(max = 255)
  String keycloakUserId,

  // Keycloak username of the account to link to the household
  @Size(max = 255)
  String username
){
  @AssertTrue(message = "give either keycloakUserId or username")
  boolean isExactlyOneAccountGiven() {
    return (keycloakUserId == null || keycloakUserId.isBlank()) != (username == null || username.isBlank());
  }
}
