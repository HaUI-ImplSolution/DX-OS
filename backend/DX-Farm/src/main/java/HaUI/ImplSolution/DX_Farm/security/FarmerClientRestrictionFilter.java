// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

// Farmers use the mobile app only: a token that carries FARMER and no staff role is accepted
// only when it was issued to one of the allowed clients (the "azp" claim). Added to the
// security chain directly, not registered as a bean, so it always runs after authentication.
public class FarmerClientRestrictionFilter extends OncePerRequestFilter {

  private static final String ROLE_FARMER = "ROLE_FARMER";
  private static final Set<String> STAFF_ROLES = Set.of("ROLE_ENGINEER", "ROLE_ADMIN");

  private final Set<String> allowedClientIds;
  private final RestAccessDeniedHandler accessDeniedHandler;

  public FarmerClientRestrictionFilter(Set<String> allowedClientIds,
      RestAccessDeniedHandler accessDeniedHandler) {
    this.allowedClientIds = allowedClientIds;
    this.accessDeniedHandler = accessDeniedHandler;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken token && isFarmerOnly(authentication)
        && !allowedClientIds.contains(authorizedParty(token))) {
      accessDeniedHandler.handle(request, response,
          new AccessDeniedException("Farmer accounts may only use the mobile app"));
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static boolean isFarmerOnly(Authentication authentication) {
    Set<String> roles = AbstractAuthenticationToken.class.cast(authentication).getAuthorities()
        .stream().map(a -> a.getAuthority()).collect(java.util.stream.Collectors.toSet());
    return roles.contains(ROLE_FARMER) && STAFF_ROLES.stream().noneMatch(roles::contains);
  }

  private static String authorizedParty(JwtAuthenticationToken token) {
    String azp = token.getToken().getClaimAsString("azp");
    return azp == null ? "" : azp;
  }
}
