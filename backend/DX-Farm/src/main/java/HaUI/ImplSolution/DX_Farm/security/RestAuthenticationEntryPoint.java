package HaUI.ImplSolution.DX_Farm.security;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

// Chạy ở tầng filter chain (trước DispatcherServlet) nên GlobalExceptionHandler không bắt
// được request thiếu/sai JWT - phải tự trả đúng khuôn ApiResponse ở đây.
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  // ObjectMapper riêng, không phụ thuộc bean autoconfigure - entry point này chạy sớm
  // trong filter chain security, muốn tách khỏi mọi cấu hình Jackson của tầng MVC.
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setCharacterEncoding("UTF-8");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(objectMapper
        .writeValueAsString(ApiResponse.error("Chưa đăng nhập hoặc token không hợp lệ")));
  }
}
