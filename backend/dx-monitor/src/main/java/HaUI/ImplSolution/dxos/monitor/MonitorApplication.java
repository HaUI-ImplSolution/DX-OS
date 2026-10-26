// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// Starts the monitoring service. It runs apart from the backend, so it keeps working when the
// backend is down.
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MonitorApplication {

  public static void main(String[] args) {
    SpringApplication.run(MonitorApplication.class, args);
  }
}
