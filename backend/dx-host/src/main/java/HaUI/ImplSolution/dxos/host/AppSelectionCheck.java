// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host;

import java.util.Set;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

// Stops the start-up before any bean is created when DXOS_APP names no known app.
public class AppSelectionCheck
    implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

  static final Set<String> KNOWN_APPS = Set.of("farm", "lab");

  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    String app = event.getEnvironment().getProperty("dxos.app", "farm");
    if (!KNOWN_APPS.contains(app)) {
      throw new IllegalStateException(
          "DXOS_APP must be one of %s but was '%s'".formatted(KNOWN_APPS, app));
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
