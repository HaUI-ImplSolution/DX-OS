// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.farm.dto.ServiceStatusResponse;
import HaUI.ImplSolution.dxos.farm.dto.ServiceStatusResponse.Service;
import java.util.List;
import org.springframework.stereotype.Component;

// Reports whether the services this backend depends on answer; the backend itself is UP when it
// responds.
@Component
public class SystemStatusService {

  private static final String UP = "UP";
  private static final String DOWN = "DOWN";

  private final AiDiagnosisClient aiClient;
  private final ObjectStorage storageService;

  public SystemStatusService(AiDiagnosisClient aiClient, ObjectStorage storageService) {
    this.aiClient = aiClient;
    this.storageService = storageService;
  }

  public ServiceStatusResponse status() {
    return new ServiceStatusResponse(
        List.of(new Service("backend", UP), new Service("ai", aiClient.isAvailable() ? UP : DOWN),
            new Service("storage", storageAvailable() ? UP : DOWN)));
  }

  private boolean storageAvailable() {
    return storageService.isReady();
  }
}
