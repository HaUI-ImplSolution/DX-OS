package HaUI.ImplSolution.DX_Farm.core.identity;

import jakarta.validation.constraints.NotBlank;

// @formatter:off
public record HouseholdLinkRequest(
  // Claim "sub" (id người dùng Keycloak) của tài khoản cần gắn vào hộ
  @NotBlank
  String keycloakUserId
){}
