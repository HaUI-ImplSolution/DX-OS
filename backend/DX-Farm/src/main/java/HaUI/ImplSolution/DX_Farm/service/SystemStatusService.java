// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.storage.StorageService;
import HaUI.ImplSolution.DX_Farm.dto.ServiceStatusResponse;
import HaUI.ImplSolution.DX_Farm.dto.ServiceStatusResponse.Service;
import io.minio.BucketExistsArgs;
import java.util.List;
import org.springframework.stereotype.Component;

// Reports whether the services this backend depends on answer; the backend itself is UP when it
// responds.
@Component
public class SystemStatusService {

  private static final String UP = "UP";
  private static final String DOWN = "DOWN";

  private final AiDiagnosisClient aiClient;
  private final StorageService storageService;

  public SystemStatusService(AiDiagnosisClient aiClient, StorageService storageService) {
    this.aiClient = aiClient;
    this.storageService = storageService;
  }

  public ServiceStatusResponse status() {
    return new ServiceStatusResponse(
        List.of(new Service("backend", UP), new Service("ai", aiClient.isAvailable() ? UP : DOWN),
            new Service("storage", storageAvailable() ? UP : DOWN)));
  }

  private boolean storageAvailable() {
    try {
      return storageService.client()
          .bucketExists(BucketExistsArgs.builder().bucket(storageService.bucket()).build());
    } catch (Exception e) {
      return false;
    }
  }
}
