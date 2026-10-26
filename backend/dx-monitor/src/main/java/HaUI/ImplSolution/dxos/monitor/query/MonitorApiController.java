// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.query;

import HaUI.ImplSolution.dxos.monitor.dto.ApiResponse;
import HaUI.ImplSolution.dxos.monitor.query.Views.AdminAction;
import HaUI.ImplSolution.dxos.monitor.query.Views.Health;
import HaUI.ImplSolution.dxos.monitor.query.Views.Summary;
import HaUI.ImplSolution.dxos.monitor.query.Views.Suspects;
import HaUI.ImplSolution.dxos.monitor.query.Views.Timeline;
import HaUI.ImplSolution.dxos.monitor.query.Views.TraceEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// The figures behind the monitoring screen. Administrators only: the security configuration
// admits no other role to /api.
@RestController
@RequestMapping("/api")
@Tag(name = "Monitor", description = "Attack and failure figures for administrators")
public class MonitorApiController {

  private final MonitorQueryService queries;

  public MonitorApiController(MonitorQueryService queries) {
    this.queries = queries;
  }

  @GetMapping("/summary")
  @Operation(summary = "Counts of refused requests, 5xx, rate-limited requests and failed sign-ins",
      description = "ADMIN only. window is 1h, 24h or 7d (400 otherwise). failedLogins is null"
          + " when the Keycloak events cannot be read.")
  public ApiResponse<Summary> summary(@RequestParam(defaultValue = "24h") String window) {
    return ApiResponse.success(queries.summary(Window.of(window)));
  }

  @GetMapping("/timeline")
  @Operation(summary = "Unusual events per column of time, oldest first",
      description = "ADMIN only. Either window (1h, 24h or 7d) or from and to (ISO-8601 instants, 1"
          + " minute to 8 days apart, for a zoomed-in span); 400 otherwise. The shorter the span"
          + " the finer the columns: 1 minute up to 1 hour, 5 minutes up to 6 hours, 1 hour up to"
          + " 48 hours, 6 hours beyond. firstAt and lastAt tell when the first and the last event"
          + " of a column happened.")
  public ApiResponse<Timeline> timeline(@RequestParam(required = false) String window,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    if (from != null || to != null) {
      return ApiResponse.success(queries.timeline(from, to));
    }
    return ApiResponse.success(queries.timeline(Window.of(window == null ? "24h" : window)));
  }

  @GetMapping("/suspects")
  @Operation(summary = "Client addresses with the most refusals, plus the paths failing most",
      description = "ADMIN only. Addresses are ranked by refused requests, rate-limited requests,"
          + " rejected tokens, unknown paths and failed sign-ins together.")
  public ApiResponse<Suspects> suspects(@RequestParam(defaultValue = "24h") String window) {
    return ApiResponse.success(queries.suspects(Window.of(window)));
  }

  @GetMapping("/health")
  @Operation(summary = "State of each probed service and its changes in the last 7 days",
      description = "ADMIN only. downSeconds is how long a DOWN lasted, null while it goes on.")
  public ApiResponse<Health> health() {
    return ApiResponse.success(queries.health());
  }

  @GetMapping("/admin-actions")
  @Operation(summary = "Recent administrative actions and failures from the audit log",
      description = "ADMIN only. Refused requests and 5xx are left out; limit is capped at 100.")
  public ApiResponse<List<AdminAction>> adminActions(@RequestParam(defaultValue = "50") int limit) {
    return ApiResponse.success(queries.adminActions(limit));
  }

  @GetMapping("/trace")
  @Operation(summary = "Gateway, backend and sign-in records, newest first",
      description = "ADMIN only. Without parameters the latest records of everything; requestId"
          + " (the X-Request-Id of a response) and ip narrow them down, both together keep what"
          + " matches both (sign-in events carry no request id, so they drop out); 400 for a"
          + " malformed value. At most 200 entries.")
  public ApiResponse<List<TraceEntry>> trace(@RequestParam(required = false) String requestId,
      @RequestParam(required = false) String ip) {
    return ApiResponse.success(queries.trace(requestId, ip));
  }
}
