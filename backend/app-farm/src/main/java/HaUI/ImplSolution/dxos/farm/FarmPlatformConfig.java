// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm;

import HaUI.ImplSolution.dxos.core.api.ai.AiGateway;
import HaUI.ImplSolution.dxos.core.api.bi.BiDatasetProvider;
import HaUI.ImplSolution.dxos.core.api.status.HealthContributor;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// What Farm adds to the shared platform services: the BI datasets it exposes and the AI service
// it reports on.
@Configuration
public class FarmPlatformConfig {

  // Views hold approved or settled records only (see V8__bi_views.sql); catalogues are public
  // data.
  @Bean
  public BiDatasetProvider farmBiDatasets() {
    return () -> List.of("vw_parcels_approved", "vw_crop_seasons_approved",
        "vw_disease_reports_processed", "vw_diagnoses_approved",
        "vw_treatment_applications_confirmed", "vw_ai_diagnosis_outcomes", "diseases", "pesticides",
        "rice_varieties");
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
