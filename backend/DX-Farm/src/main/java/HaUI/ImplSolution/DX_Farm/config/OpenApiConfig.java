package HaUI.ImplSolution.DX_Farm.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String BEARER_SCHEME = "bearerAuth";
  private static final String SERVICE_API_KEY_SCHEME = "serviceApiKey";

  @Bean
  public OpenAPI dxFarmOpenApi() {
    return new OpenAPI()
        .info(new Info().title("DX-Farm API").version("v1")
            .description("Agri-Carbon DX-OS backend — operational data, structured knowledge lookup"
                + " and approval workflow for the spine flow."))
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
