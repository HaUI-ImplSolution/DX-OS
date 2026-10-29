// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.security;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.security.RateLimitRule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.ObjectMapper;

// Answers 429 when a signed-in user (the "sub" of the token) calls a path more often than its
// rules allow. Added to the security chain directly, so it always runs after authentication.
public class UserRateLimitFilter extends OncePerRequestFilter {

  private record Limit(String method, PathPattern pattern, int maxPerMinute, String key) {}

  private final List<Limit> limits;
  private final UserRateLimiter limiter;
  private final ObjectMapper objectMapper;

  public UserRateLimitFilter(List<RateLimitRule> rules, UserRateLimiter limiter,
      ObjectMapper objectMapper) {
    PathPatternParser parser = new PathPatternParser();
    this.limits =
        rules.stream().map(rule -> new Limit(rule.method(), parser.parse(rule.pathPattern()),
            rule.maxPerMinute(), rule.method() + " " + rule.pathPattern())).toList();
    this.limiter = limiter;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken token && token.getName() != null) {
      long retryAfter = admit(token.getName(), request);
      if (retryAfter > 0) {
        reject(response, retryAfter);
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  // A call counts against every rule that matches it; the longest wait wins.
  private long admit(String user, HttpServletRequest request) {
    String path = request.getRequestURI();
    long now = System.currentTimeMillis();
    long retryAfter = 0;
    for (Limit limit : limits) {
      if ((limit.method() == null || limit.method().equals(request.getMethod()))
          && limit.pattern().matches(PathContainer.parsePath(path))) {
        retryAfter = Math.max(retryAfter,
            limiter.admit(user + "|" + limit.key(), limit.maxPerMinute(), now));
      }
    }
    return retryAfter;
  }

  private void reject(HttpServletResponse response, long retryAfter) throws IOException {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
    response.setCharacterEncoding("UTF-8");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(objectMapper.writeValueAsString(ApiResponse
        .error("ERR_TOO_MANY_REQUESTS", "Bạn thao tác quá nhanh, vui lòng thử lại sau ít phút")));
  }
}
