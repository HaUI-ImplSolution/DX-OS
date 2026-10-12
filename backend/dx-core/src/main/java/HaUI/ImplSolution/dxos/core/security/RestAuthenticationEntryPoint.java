// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.security;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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

  public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setCharacterEncoding("UTF-8");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(objectMapper.writeValueAsString(
        ApiResponse.error("ERR_UNAUTHENTICATED", "Chưa đăng nhập hoặc token không hợp lệ")));
  }
}
