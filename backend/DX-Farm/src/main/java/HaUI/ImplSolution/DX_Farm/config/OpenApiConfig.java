package HaUI.ImplSolution.DX_Farm.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI dxFarmOpenApi() {
    return new OpenAPI().info(new Info().title("DX-Farm API").version("v1")
        .description("Agri-Carbon DX-OS backend — operational data, structured knowledge lookup"
            + " and approval workflow for the spine flow."));
  }
}
