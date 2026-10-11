// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.status;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "Service status for the portal")
public class SystemStatusController {

  private final SystemStatusService systemStatusService;

  public SystemStatusController(SystemStatusService systemStatusService) {
    this.systemStatusService = systemStatusService;
  }

  @GetMapping("/status")
  @Operation(summary = "Status of the backend, AI service and photo storage",
      description = "Public, no sign-in needed. Each service is UP or DOWN.")
  public ApiResponse<ServiceStatusResponse> status() {
    return ApiResponse.success(systemStatusService.status());
  }
}
