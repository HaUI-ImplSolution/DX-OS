// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.config;

import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Single source of "today" for business rules (deadlines, date checks), independent of
// the container time zone.
@Configuration
public class TimeConfig {

  @Bean
  public Clock clock() {
    return Clock.system(BusinessTime.ZONE);
  }
}
