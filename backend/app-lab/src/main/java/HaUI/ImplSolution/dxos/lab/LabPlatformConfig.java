// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab;

import HaUI.ImplSolution.dxos.core.api.bi.BiDatasetProvider;
import HaUI.ImplSolution.dxos.core.api.status.HealthContributor;
import HaUI.ImplSolution.dxos.core.api.ai.AiGateway;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// What Lab adds to the shared platform services: the BI views it exposes and the AI service it
// reports on.
@Configuration
public class LabPlatformConfig {

  // Views carry ids, codes and groups only, see V2__bi_views.sql.
  @Bean
  public BiDatasetProvider labBiDatasets() {
    return () -> List.of("vw_lab_rooms", "vw_lab_devices", "vw_lab_tickets", "vw_lab_device_loans",
        "vw_lab_room_bookings");
  }

  @Bean
  public HealthContributor aiHealth(AiGateway aiGateway) {
    return new HealthContributor() {
      @Override
      public String name() {
        return "ai";
      }

      @Override
      public boolean isUp() {
        return aiGateway.isAvailable();
      }
    };
  }
}
