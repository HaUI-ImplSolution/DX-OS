// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceDirectory;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

// Actor cho reportedBy/decidedBy... lấy từ claim của token Keycloak đã xác thực.
@Service
public class CurrentUserService implements UserContext {

  private static final String ROLE_FARMER = "ROLE_FARMER";
  private static final String ROLE_ADMIN = "ROLE_ADMIN";
  private static final Set<String> STAFF_ROLES = Set.of("ROLE_ENGINEER", ROLE_ADMIN);

  private final WorkspaceDirectory workspaceDirectory;

  public CurrentUserService(WorkspaceDirectory workspaceDirectory) {
    this.workspaceDirectory = workspaceDirectory;
  }

  public Jwt getCurrentJwt() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (principal instanceof Jwt jwt) {
      return jwt;
    }
    throw new IllegalStateException("No authenticated Jwt principal found: " + principal);
  }

  @Override
  public String currentFullName() {
    Jwt jwt = getCurrentJwt();
    String name = jwt.getClaimAsString("name");
    return name != null ? name : jwt.getClaimAsString("preferred_username");
  }

  // Stable account id (token "sub"), unlike the display name which Keycloak users can change.
  @Override
  public String currentUserId() {
    return getCurrentJwt().getSubject();
  }

  @Override
  public Optional<WorkspaceView> currentWorkspace() {
    return workspaceDirectory.findByAccount(currentUserId());
  }

  // A FARMER without ENGINEER/ADMIN is limited to their own household's parcels and approved
  // diagnoses.
  @Override
  public boolean isFarmer() {
    Set<String> roles = currentRoles();
    return roles.contains(ROLE_FARMER) && STAFF_ROLES.stream().noneMatch(roles::contains);
  }

  @Override
  public boolean isAdmin() {
    return currentRoles().contains(ROLE_ADMIN);
  }

  private Set<String> currentRoles() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return Set.of();
    }
    return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
  }
}
