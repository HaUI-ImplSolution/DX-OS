// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import HaUI.ImplSolution.dxos.core.api.security.ClientRestrictionPolicy;
import HaUI.ImplSolution.dxos.core.api.security.RateLimitRule;
import HaUI.ImplSolution.dxos.core.audit.AuditDenialLimiter;
import HaUI.ImplSolution.dxos.core.audit.RequestTraceFilter;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

// The filters in front of every request: who is turned away, who is slowed down, who may read the
// metrics, and what is kept about a request for the audit log.
class RequestFiltersTest {

  @AfterEach
  void signOut() {
    SecurityContextHolder.clearContext();
  }

  private static void signIn(String client, String... roles) {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject("user-1")
            .claim("azp", client)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                jwt,
                java.util.Arrays.stream(roles)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList()));
  }

  private static MockHttpServletRequest request(String method, String path) {
    return new MockHttpServletRequest(method, path);
  }

  private static boolean refused(String client, String... roles) throws Exception {
    signIn(client, roles);
    List<AccessDeniedException> denied = new ArrayList<>();
    AccessDeniedHandler handler = (request, response, reason) -> denied.add(reason);
    ClientRestrictionPolicy policy = mock(ClientRestrictionPolicy.class);
    org.mockito.Mockito.when(policy.role()).thenReturn("MACHINE");
    org.mockito.Mockito.when(policy.allowedClientIds()).thenReturn(Set.of("device"));
    MockFilterChain chain = new MockFilterChain();
    new ClientRestrictionFilter(List.of(policy), Set.of("ADMIN"), handler)
        .doFilter(request("GET", "/api/v1/data"), new MockHttpServletResponse(), chain);
    return !denied.isEmpty() && chain.getRequest() == null;
  }

  @Test
  void aRestrictedRoleWorksOnlyThroughItsOwnClient() throws Exception {
    assertThat(refused("web", "MACHINE")).isTrue();
    assertThat(refused("device", "MACHINE")).isFalse();
    assertThat(refused("web", "MACHINE", "ADMIN")).isFalse();
    assertThat(refused("web", "BUYER")).isFalse();
  }

  @Test
  void aUserAboveTheLimitIsToldToWait() throws Exception {
    UserRateLimitFilter filter =
        new UserRateLimitFilter(
            List.of(new RateLimitRule("POST", "/api/v1/data/**", 1)),
            new UserRateLimiter(),
            new ObjectMapper());
    signIn("web", "BUYER");

    MockHttpServletResponse first = new MockHttpServletResponse();
    filter.doFilter(request("POST", "/api/v1/data/a"), first, new MockFilterChain());
    MockHttpServletResponse second = new MockHttpServletResponse();
    filter.doFilter(request("POST", "/api/v1/data/b"), second, new MockFilterChain());
    MockHttpServletResponse read = new MockHttpServletResponse();
    filter.doFilter(request("GET", "/api/v1/data/a"), read, new MockFilterChain());

    assertThat(first.getStatus()).isEqualTo(200);
    assertThat(second.getStatus()).isEqualTo(429);
    assertThat(second.getHeader("Retry-After")).isNotBlank();
    assertThat(second.getContentAsString()).contains("ERR_TOO_MANY_REQUESTS");
    assertThat(read.getStatus()).isEqualTo(200);
  }

  private static Object metricsCaller(String password, String path, String header)
      throws Exception {
    SecurityContextHolder.clearContext();
    MockHttpServletRequest request = request("GET", path);
    if (header != null) {
      request.addHeader("Authorization", header);
    }
    new MetricsBasicAuthFilter(password)
        .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    return SecurityContextHolder.getContext().getAuthentication();
  }

  private static String basic(String pair) {
    return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void onlyTheScrapePasswordOpensTheMetrics() throws Exception {
    String path = "/actuator/prometheus";
    assertThat(metricsCaller("pw", path, basic("metrics:pw"))).isNotNull();
    assertThat(metricsCaller("pw", path, basic("metrics:other"))).isNull();
    assertThat(metricsCaller("pw", path, basic("admin:pw"))).isNull();
    assertThat(metricsCaller("pw", path, "Basic ***")).isNull();
    assertThat(metricsCaller("pw", path, null)).isNull();
    assertThat(metricsCaller("", path, basic("metrics:"))).isNull();
    assertThat(metricsCaller("pw", "/api/v1/data", basic("metrics:pw"))).isNull();
  }

  @Test
  void aRequestKeepsASafeIdAndTheAddressOfTheCaller() throws Exception {
    List<String> seen = new ArrayList<>();
    FilterChain chain =
        (request, response) -> {
          seen.add(MDC.getCopyOfContextMap().values().toString());
        };
    MockHttpServletRequest good = request("GET", "/api/v1/data");
    good.addHeader("X-Request-Id", "abc-1");
    good.addHeader("X-Real-IP", "203.0.113.7");
    MockHttpServletRequest forged = request("GET", "/api/v1/data");
    forged.addHeader("X-Request-Id", "bad id\nline");

    new RequestTraceFilter().doFilter(good, new MockHttpServletResponse(), chain);
    new RequestTraceFilter().doFilter(forged, new MockHttpServletResponse(), chain);

    assertThat(seen.get(0)).contains("abc-1", "203.0.113.7");
    assertThat(seen.get(1)).doesNotContain("bad id");
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  @Test
  void repeatedRefusalsAreRecordedOnlyUpToALimit() {
    AuditDenialLimiter limiter = new AuditDenialLimiter(2);

    assertThat(limiter.admit("u", 0)).isZero();
    assertThat(limiter.admit("u", 1)).isZero();
    assertThat(limiter.admit("u", 2)).isNegative();
    assertThat(limiter.admit("other", 2)).isZero();
    assertThat(limiter.admit("u", 61_000)).isEqualTo(1);
  }
}
