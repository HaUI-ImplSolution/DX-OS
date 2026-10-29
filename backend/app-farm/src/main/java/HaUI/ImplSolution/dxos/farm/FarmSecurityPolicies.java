// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm;

import HaUI.ImplSolution.dxos.core.api.security.ClientRestrictionPolicy;
import HaUI.ImplSolution.dxos.core.api.security.EndpointPolicyContributor;
import HaUI.ImplSolution.dxos.core.api.security.RateLimitContributor;
import HaUI.ImplSolution.dxos.core.api.security.RateLimitRule;
import HaUI.ImplSolution.dxos.core.api.security.RoleDefinition;
import HaUI.ImplSolution.dxos.core.api.security.RoleProvider;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Farm's roles, its public catalogs and the mobile-only rule for farmers.
@Configuration
public class FarmSecurityPolicies {

  public static final String FARMER = "FARMER";
  public static final String ENGINEER = "ENGINEER";
  public static final String ADMIN = "ADMIN";

  @Bean
  public RoleProvider farmRoleProvider() {
    return () -> List.of(new RoleDefinition(FARMER, "Farmer of a household", false),
        new RoleDefinition(ENGINEER, "Agricultural engineer who reviews reports", true),
        new RoleDefinition(ADMIN, "Cooperative administrator", true));
  }

  @Bean
  public EndpointPolicyContributor farmPublicEndpoints() {
    return () -> List.of("/api/v1/diseases/**", "/api/v1/pesticides/**",
        "/api/v1/treatment-rules/**");
  }

  // Per user and minute: the calls that upload an image or run the AI service.
  @Bean
  public RateLimitContributor farmRateLimits() {
    return () -> List.of(
        RateLimitRule.post("/api/v1/crop-seasons/{cropSeasonId}/disease-reports", 10),
        RateLimitRule.post("/api/v1/disease-reports/precheck", 20),
        RateLimitRule.post("/api/v1/disease-reports/{reportId}/diagnosis/ai", 6),
        RateLimitRule.post("/api/v1/disease-reports/{reportId}/diagnosis/suggest", 20));
  }

  // Farmers use the mobile app only: the clients come from security.farmer-client-ids.
  @Bean
  public ClientRestrictionPolicy farmerClientRestriction(
      @Value("${security.farmer-client-ids}") String farmerClientIds) {
    Set<String> clients = Set.of(farmerClientIds.trim().split("\\s*,\\s*"));
    return new ClientRestrictionPolicy() {
      @Override
      public String role() {
        return FARMER;
      }

      @Override
      public Set<String> allowedClientIds() {
        return clients;
      }
    };
  }
}
