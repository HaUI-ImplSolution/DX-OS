// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.security;

import HaUI.ImplSolution.dxos.core.api.security.ClientRestrictionPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

// Applies the app's client restrictions: a token that carries a restricted role and no staff role
// is accepted only when it was issued to one of the allowed clients (the "azp" claim). Added to
// the security chain directly, not registered as a bean, so it always runs after authentication.
public class ClientRestrictionFilter extends OncePerRequestFilter {

  private final List<ClientRestrictionPolicy> policies;
  private final Set<String> staffAuthorities;
  private final AccessDeniedHandler accessDeniedHandler;

  public ClientRestrictionFilter(List<ClientRestrictionPolicy> policies, Set<String> staffRoles,
      AccessDeniedHandler accessDeniedHandler) {
    this.policies = policies;
    this.staffAuthorities =
        staffRoles.stream().map(role -> "ROLE_" + role).collect(Collectors.toSet());
    this.accessDeniedHandler = accessDeniedHandler;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken token && isRejected(token)) {
      accessDeniedHandler.handle(request, response,
          new AccessDeniedException("This account may not use this client"));
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean isRejected(JwtAuthenticationToken token) {
    Set<String> authorities = token.getAuthorities().stream().map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
    if (authorities.stream().anyMatch(staffAuthorities::contains)) {
      return false;
    }
    String client = token.getToken().getClaimAsString("azp");
    return policies.stream().anyMatch(policy -> authorities.contains("ROLE_" + policy.role())
        && !policy.allowedClientIds().contains(client == null ? "" : client));
  }
}
