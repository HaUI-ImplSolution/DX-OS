// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.status;

import HaUI.ImplSolution.dxos.core.api.status.HealthContributor;
import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.core.status.ServiceStatusResponse.Service;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

// Reports whether the services this backend depends on answer; the backend itself is UP when it
// responds. The app adds its own services through HealthContributor.
@Component
public class SystemStatusService {

  private static final String UP = "UP";
  private static final String DOWN = "DOWN";

  private final ObjectStorage objectStorage;
  private final ObjectProvider<HealthContributor> contributors;

  public SystemStatusService(ObjectStorage objectStorage,
      ObjectProvider<HealthContributor> contributors) {
    this.objectStorage = objectStorage;
    this.contributors = contributors;
  }

  public ServiceStatusResponse status() {
    List<Service> services = new ArrayList<>();
    services.add(new Service("backend", UP));
    contributors.orderedStream()
        .forEach(contributor -> services.add(new Service(contributor.name(), state(contributor))));
    services.add(new Service("storage", objectStorage.isReady() ? UP : DOWN));
    return new ServiceStatusResponse(services);
  }

  private static String state(HealthContributor contributor) {
    try {
      return contributor.isUp() ? UP : DOWN;
    } catch (RuntimeException e) {
      return DOWN;
    }
  }
}
