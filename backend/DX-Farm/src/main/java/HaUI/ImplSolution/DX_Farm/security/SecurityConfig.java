// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.security;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// OAuth2 Resource Server: chỉ xác thực token do Keycloak cấp, không tự quản lý user/password.
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private static final String[] PUBLIC_GET_ENDPOINTS =
      {"/api/v1/diseases/**", "/api/v1/pesticides/**", "/api/v1/treatment-rules/**"};

  private static final String SYSTEM_STATUS_ENDPOINT = "/api/v1/system/status";

  private static final String[] SWAGGER_ENDPOINTS =
      {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

  // Docker healthcheck gọi endpoint này không kèm Authorization header.
  private static final String ACTUATOR_HEALTH_ENDPOINT = "/actuator/health/**";

  // Validates the signature and expiry like the default decoder, plus the audience: tokens that
  // were not issued for this API are rejected. The issuer is deliberately not checked, see
  // sso/README.md.
  @Bean
  public JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
      @Value("${security.jwt.audience}") String audience) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(),
        audienceValidator(audience)));
    return decoder;
  }

  static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
    return new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
        audiences -> audiences != null && audiences.contains(audience));
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
      RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler,
      ServiceApiKeyAuthenticationFilter serviceApiKeyAuthenticationFilter,
      @Value("${security.farmer-client-ids}") String farmerClientIds) throws Exception {
    http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler))
        .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(SWAGGER_ENDPOINTS).permitAll()
            .requestMatchers(HttpMethod.GET, ACTUATOR_HEALTH_ENDPOINT).permitAll()
            .requestMatchers(HttpMethod.GET, SYSTEM_STATUS_ENDPOINT).permitAll()
            .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
            // AI chỉ được gọi GET qua service API key - mọi method khác ở /internal/v1/**
            // bị chặn hẳn, kể cả nếu lỡ có endpoint ghi được thêm sau này.
            .requestMatchers(HttpMethod.GET, "/internal/v1/**").hasAuthority("ROLE_AI_SERVICE")
            .requestMatchers("/internal/v1/**").denyAll().anyRequest().authenticated())
        .addFilterBefore(serviceApiKeyAuthenticationFilter, BearerTokenAuthenticationFilter.class)
        .addFilterAfter(
            new FarmerClientRestrictionFilter(Set.of(farmerClientIds.trim().split("\\s*,\\s*")),
                accessDeniedHandler),
            BearerTokenAuthenticationFilter.class)
        .oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(authenticationEntryPoint)
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
    return http.build();
  }

  private JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
    return converter;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
