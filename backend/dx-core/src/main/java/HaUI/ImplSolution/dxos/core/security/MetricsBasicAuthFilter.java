// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

// Lets a metrics scraper read /actuator/prometheus with HTTP Basic: the user "metrics" and the
// password of metrics.scrape-password. Basic is used because a scraper does not hold a Keycloak
// token, and because the resource server ignores it. Without a password nobody gets in (fail
// closed): the request falls to the rule that the endpoint needs the metrics authority.
@Component
public class MetricsBasicAuthFilter extends OncePerRequestFilter {

  static final String PATH = "/actuator/prometheus";
  static final String USER = "metrics";
  static final String AUTHORITY = "ROLE_METRICS";

  private final byte[] expected;

  public MetricsBasicAuthFilter(@Value("${metrics.scrape-password:}") String password) {
    this.expected = password.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (PATH.equals(request.getRequestURI()) && expected.length > 0 && matches(request)) {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  USER, null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
    }
    filterChain.doFilter(request, response);
  }

  private boolean matches(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (!StringUtils.hasText(header) || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
      return false;
    }
    try {
      String pair =
          new String(
              Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
      int colon = pair.indexOf(':');
      return colon > 0
          && USER.equals(pair.substring(0, colon))
          && MessageDigest.isEqual(
              expected, pair.substring(colon + 1).getBytes(StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
