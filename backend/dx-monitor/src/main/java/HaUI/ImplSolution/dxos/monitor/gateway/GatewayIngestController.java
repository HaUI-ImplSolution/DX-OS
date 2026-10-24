// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.gateway;

import HaUI.ImplSolution.dxos.monitor.config.MonitorProperties;
import HaUI.ImplSolution.dxos.monitor.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Receives the batches of log entries the gateway sends. Only the gateway reaches this path: the
// public route of the gateway does not forward it, and the shared key is checked here.
@RestController
@RequestMapping("/ingest")
@Hidden
public class GatewayIngestController {

  private static final int MAX_BATCH = 1000;
  private static final String SCHEME = "Monitor ";

  private final byte[] expectedHeader;
  private final GatewayEventFilter filter;
  private final GatewayEventStore store;

  public GatewayIngestController(MonitorProperties properties, GatewayEventFilter filter,
      GatewayEventStore store) {
    String key = properties.gateway().ingestKey();
    this.expectedHeader =
        key == null || key.isBlank() ? null : (SCHEME + key).getBytes(StandardCharsets.UTF_8);
    this.filter = filter;
    this.store = store;
  }

  @PostMapping("/gateway")
  @Operation(summary = "Receive a batch of gateway log entries",
      description = "Gateway only: 401 without the shared key (and for every request while no key"
          + " is configured), 400 for a batch above " + MAX_BATCH + " entries. Ordinary traffic is"
          + " dropped, the rest is kept; 202 with the number kept.")
  public ResponseEntity<ApiResponse<Map<String, Integer>>> receive(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestBody List<Map<String, Object>> entries) {
    if (!authorized(authorization)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (entries.size() > MAX_BATCH) {
      return ResponseEntity.badRequest().build();
    }
    List<GatewayEvent> events =
        entries.stream().map(filter::classify).flatMap(Optional::stream).toList();
    int kept = store.saveAll(events);
    return ResponseEntity.accepted().body(ApiResponse.success(Map.of("kept", kept)));
  }

  private boolean authorized(String authorization) {
    return expectedHeader != null && authorization != null
        && MessageDigest.isEqual(expectedHeader, authorization.getBytes(StandardCharsets.UTF_8));
  }
}
