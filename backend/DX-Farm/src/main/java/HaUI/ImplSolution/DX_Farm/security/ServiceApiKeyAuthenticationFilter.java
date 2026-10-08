// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

// Xác thực service token cho AI -> Backend ở /internal/v1/**: API key nội bộ qua header, không
// phải JWT người dùng qua Keycloak. Không cấu hình ai.service.inbound-api-key thì filter không
// cấp quyền cho ai cả (fail closed) - request rơi vào anyRequest().authenticated().
@Component
public class ServiceApiKeyAuthenticationFilter extends OncePerRequestFilter {

  private static final String HEADER_NAME = "X-Service-Api-Key";
  private static final String INTERNAL_PATH_PREFIX = "/internal/v1/";

  private final String expectedApiKey;

  public ServiceApiKeyAuthenticationFilter(
      @Value("${ai.service.inbound-api-key:}") String expectedApiKey) {
    this.expectedApiKey = expectedApiKey;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    if (request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX)
        && StringUtils.hasText(expectedApiKey)
        && expectedApiKey.equals(request.getHeader(HEADER_NAME))) {
      SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
          "ai-service", null, List.of(new SimpleGrantedAuthority("ROLE_AI_SERVICE"))));
    }
    filterChain.doFilter(request, response);
  }
}
