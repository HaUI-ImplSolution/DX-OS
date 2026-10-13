// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

// Starts the shared core and the one app chosen by DXOS_APP.
@SpringBootApplication
public class DxosApplication {

  public static void main(String[] args) {
    new SpringApplicationBuilder(DxosApplication.class).listeners(new AppSelectionCheck())
        .run(args);
  }
}
