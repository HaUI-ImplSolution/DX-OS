// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.config;

import com.nimbusds.jwt.JWTParser;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

// The screen and its figures are for administrators who signed in through Keycloak. The gateway
// delivery path is outside the session rules and protected by its own shared key. After sign-in
// the browser returns to the public address, whatever address the gateway forwards.
@Configuration
@EnableWebSecurity
public class MonitorSecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(MonitorSecurityConfig.class);
  private static final String ROLE_PREFIX = "ROLE_";
  private static final String CONTENT_SECURITY_POLICY =
      "default-src 'self'; img-src 'self' data:; " + "frame-ancestors 'none'; base-uri 'self'";

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
      OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService,
      LogoutSuccessHandler keycloakLogout, MonitorProperties properties) throws Exception {
    HttpStatusEntryPoint unauthorized = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
    http.authorizeHttpRequests(requests -> requests.requestMatchers("/actuator/health",
        "/actuator/health/**", "/ingest/**", "/forbidden", "/assets/**", "/error").permitAll()
        .anyRequest().hasRole("ADMIN"))
        .oauth2Login(login -> login.userInfoEndpoint(info -> info.oidcUserService(oidcUserService))
            .defaultSuccessUrl(properties.publicUrl() + "/monitor/", true))
        .logout(logout -> logout.logoutSuccessHandler(keycloakLogout))
        .csrf(csrf -> csrf.ignoringRequestMatchers("/ingest/**"))
        .exceptionHandling(handling -> handling
            .defaultAuthenticationEntryPointFor(unauthorized,
                PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
            .accessDeniedHandler((request, response, denied) -> {
              if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                response.sendError(HttpStatus.FORBIDDEN.value());
                return;
              }
              if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
              }
              response.sendRedirect(request.getContextPath() + "/forbidden");
            }))
        .headers(headers -> headers
            .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
            .frameOptions(frame -> frame.deny()))
        .httpBasic(basic -> basic.disable());
    return http.build();
  }

  // The roles of the realm are in the access token, not in the ID token. The token comes straight
  // from the token endpoint of Keycloak, so its payload is read without checking the signature.
  @Bean
  public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
    OidcUserService delegate = new OidcUserService();
    return request -> {
      OidcUser user = delegate.loadUser(request);
      Set<GrantedAuthority> authorities = new HashSet<>(user.getAuthorities());
      for (String role : realmRoles(request.getAccessToken().getTokenValue())) {
        authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role));
      }
      String nameClaim =
          user.getIdToken().hasClaim("preferred_username") ? "preferred_username" : "sub";
      return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo(), nameClaim);
    };
  }

  // Ends the Keycloak session too, so the other applications sign out with the screen.
  @Bean
  public LogoutSuccessHandler keycloakLogout(MonitorProperties properties,
      @Value("${spring.security.oauth2.client.registration.keycloak.client-id}") String clientId,
      @Value("${monitor.keycloak-logout-url}") String logoutUrl) {
    return (request, response, authentication) -> {
      StringBuilder url = new StringBuilder(logoutUrl).append("?client_id=")
          .append(encode(clientId)).append("&post_logout_redirect_uri=")
          .append(encode(properties.publicUrl() + request.getContextPath() + "/"));
      if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) {
        url.append("&id_token_hint=").append(encode(user.getIdToken().getTokenValue()));
      }
      response.sendRedirect(url.toString());
    };
  }

  @SuppressWarnings("unchecked")
  private static List<String> realmRoles(String accessToken) {
    try {
      Map<String, Object> access =
          JWTParser.parse(accessToken).getJWTClaimsSet().getJSONObjectClaim("realm_access");
      Object roles = access == null ? null : access.get("roles");
      return roles instanceof List<?> list ? (List<String>) list : List.of();
    } catch (Exception e) {
      log.warn("The access token has no readable realm roles: {}", e.getClass().getSimpleName());
      return List.of();
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
