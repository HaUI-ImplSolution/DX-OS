// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.security;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// Chạy ở tầng filter chain (trước DispatcherServlet) nên GlobalExceptionHandler không bắt
// được request thiếu/sai JWT - phải tự trả đúng khuôn ApiResponse ở đây.
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;
  private final AuditService auditService;

  public RestAuthenticationEntryPoint(ObjectMapper objectMapper, AuditService auditService) {
    this.objectMapper = objectMapper;
    this.auditService = auditService;
  }

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException {
    // A request without any token is mostly scanning noise; only a refused token is recorded.
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      auditService.recordDenial("AUTH_FAILED");
    }
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setCharacterEncoding("UTF-8");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(objectMapper.writeValueAsString(
        ApiResponse.error("ERR_UNAUTHENTICATED", "Chưa đăng nhập hoặc token không hợp lệ")));
  }
}
