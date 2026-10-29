// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.config;

import HaUI.ImplSolution.dxos.core.api.app.AppModule;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String BEARER_SCHEME = "bearerAuth";
  private static final String SERVICE_API_KEY_SCHEME = "serviceApiKey";

  @Bean
  public OpenAPI dxosOpenApi(ObjectProvider<AppModule> appModules) {
    String appName = appModules.stream().findFirst().map(AppModule::name).orElse("DX-OS");
    return new OpenAPI().info(new Info().title(appName + " API").version("v1")
        .description("DX-OS backend — operational data, structured knowledge lookup and"
            + " approval workflow of the running app. Calls that create a record accept an"
            + " Idempotency-Key header (a UUID). A user who calls a costly endpoint too often in"
            + " one minute gets 429 with a Retry-After header."))
        .components(new Components()
            .addSecuritySchemes(BEARER_SCHEME,
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                    .bearerFormat("JWT"))
            .addSecuritySchemes(SERVICE_API_KEY_SCHEME,
                new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                    .name("X-Service-Api-Key")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
  }
}
