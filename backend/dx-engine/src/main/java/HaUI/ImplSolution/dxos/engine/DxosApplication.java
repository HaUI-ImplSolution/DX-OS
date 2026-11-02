// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Starts the shared core and the model-driven service.
@SpringBootApplication
public class DxosApplication {

  public static void main(String[] args) {
    SpringApplication.run(DxosApplication.class, args);
  }
}
