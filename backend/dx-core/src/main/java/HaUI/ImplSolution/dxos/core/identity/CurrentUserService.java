// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceDirectory;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;
import HaUI.ImplSolution.dxos.core.security.AppRoles;
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

  private static final String ROLE_PREFIX = "ROLE_";

  private final WorkspaceDirectory workspaceDirectory;
  private final AppRoles appRoles;

  public CurrentUserService(WorkspaceDirectory workspaceDirectory, AppRoles appRoles) {
    this.workspaceDirectory = workspaceDirectory;
    this.appRoles = appRoles;
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

  @Override
  public Set<String> roles() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return Set.of();
    }
    return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
        .filter(authority -> authority.startsWith(ROLE_PREFIX))
        .map(authority -> authority.substring(ROLE_PREFIX.length()))
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public boolean hasRole(String role) {
    return roles().contains(role);
  }

  @Override
  public boolean isStaff() {
    Set<String> roles = roles();
    return appRoles.staffRoles().stream().anyMatch(roles::contains);
  }
}
