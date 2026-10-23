// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Puts the request id, client address, method and path in the logging context, so every log line
// and audit record of the request carries them. The gateway sets X-Request-Id and X-Real-IP; the
// backend is only reachable through it.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

  private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final int MAX_ADDRESS = 64;
  private static final int MAX_PATH = 512;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String requestId = request.getHeader("X-Request-Id");
    if (requestId == null || !SAFE_ID.matcher(requestId).matches()) {
      requestId = UUID.randomUUID().toString();
    }
    MDC.put(RequestContext.REQUEST_ID, requestId);
    MDC.put(RequestContext.CLIENT_IP, clientAddress(request));
    MDC.put(RequestContext.METHOD, request.getMethod());
    MDC.put(RequestContext.PATH, truncate(request.getRequestURI(), MAX_PATH));
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(RequestContext.REQUEST_ID);
      MDC.remove(RequestContext.CLIENT_IP);
      MDC.remove(RequestContext.METHOD);
      MDC.remove(RequestContext.PATH);
    }
  }

  private static String clientAddress(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Real-IP");
    String address =
        forwarded != null && !forwarded.isBlank() ? forwarded.strip() : request.getRemoteAddr();
    return truncate(address, MAX_ADDRESS);
  }

  private static String truncate(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }
}
