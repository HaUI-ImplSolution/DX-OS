// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FarmAppModuleTest {

  @Test
  void manifestSettingsCarryTheGpsAccuracyLimit() {
    var settings = new FarmAppModule(new BigDecimal("30.00")).settings();

    assertThat(settings).containsEntry(FarmAppModule.GPS_MAX_ACCURACY_SETTING, "30");
  }

  @Test
  void fractionalLimitIsKept() {
    var settings = new FarmAppModule(new BigDecimal("25.5")).settings();

    assertThat(settings).containsEntry(FarmAppModule.GPS_MAX_ACCURACY_SETTING, "25.5");
  }
}
