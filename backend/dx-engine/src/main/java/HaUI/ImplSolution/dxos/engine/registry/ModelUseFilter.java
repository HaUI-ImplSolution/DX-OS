// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.registry;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Lets go of the models a request used once the request is over, after its transaction ended,
// whether it succeeded or failed.
@Component
public class ModelUseFilter extends OncePerRequestFilter {

  private final ModelRegistry registry;

  public ModelUseFilter(ModelRegistry registry) {
    this.registry = registry;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    registry.startRequest();
    try {
      chain.doFilter(request, response);
    } finally {
      registry.endRequest();
    }
  }
}
