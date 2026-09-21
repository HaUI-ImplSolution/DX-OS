// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.security;

import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.access.AccessDeniedHandler;

// Farm's additions to the shared security chain: public catalogs and the mobile-only rule for
// farmers.
@Configuration
public class FarmSecurityConfig {

  private static final String[] PUBLIC_GET_ENDPOINTS = {"/api/v1/diseases/**",
      "/api/v1/pesticides/**", "/api/v1/treatment-rules/**", "/api/v1/system/status"};

  @Bean
  public Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> farmPublicEndpoints() {
    return auth -> auth.requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll();
  }

  @Bean
  public Customizer<HttpSecurity> farmerClientRestriction(
      @Value("${security.farmer-client-ids}") String farmerClientIds,
      AccessDeniedHandler accessDeniedHandler) {
    return http -> http.addFilterAfter(
        new FarmerClientRestrictionFilter(Set.of(farmerClientIds.trim().split("\\s*,\\s*")),
            accessDeniedHandler),
        BearerTokenAuthenticationFilter.class);
  }
}
