// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.query;

import java.time.Instant;
import java.util.List;

// What the monitoring API returns. A count that cannot be read (the Keycloak database is not
// reachable) is null.
public final class Views {

  private Views() {}

  // serverErrors: 5xx seen by the gateway; backendErrors: 5xx the backend recorded itself
  public record Summary(String window, long denied, long serverErrors, long backendErrors,
      long rateLimited, Long failedLogins, long servicesDown) {}

  // firstAt, lastAt: when the first and the last event counted in the column happened;
  // loginFirstAt, loginLastAt: the same for the failed sign-ins
  public record Bucket(Instant start, long denied, long rateLimited, long authRejected,
      long notFound, long serverErrors, long slow, Long failedLogins, Instant firstAt,
      Instant lastAt, Instant loginFirstAt, Instant loginLastAt) {}

  // window: 1h, 24h, 7d, or custom for a span the user zoomed into
  public record Timeline(String window, Instant from, Instant to, long bucketSeconds,
      List<Bucket> buckets) {}

  public record Suspect(String ip, long denied, long rateLimited, long authRejected, long notFound,
      long failedLogins) {}

  public record ErrorPath(String path, long count) {}

  public record Suspects(String window, List<Suspect> addresses, List<ErrorPath> errorPaths) {}

  public record ServiceState(String service, String state, String detail, Instant since) {}

  public record StateChange(String service, String state, String detail, Instant at,
      Long downSeconds) {}

  public record Health(List<ServiceState> services, List<StateChange> changes) {}

  public record AdminAction(Instant at, String action, String outcome, String actor, String roles,
      String target, String ip, String path, String requestId) {}

  public record TraceEntry(Instant at, String source, String what, String result, String ip,
      String method, String path, String requestId, String detail) {}
}
