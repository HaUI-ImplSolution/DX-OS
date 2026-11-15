// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Settings under "monitor" in application.yaml.
//
// retentionDays: age after which gateway events and service state changes are deleted
@ConfigurationProperties(prefix = "monitor")
public record MonitorProperties(
    String publicUrl,
    Gateway gateway,
    Probe probe,
    Alert alert,
    int retentionDays,
    KeycloakDb keycloakDb,
    ChatWebhook chatWebhook) {

  // ingestKey: shared secret of the gateway; empty refuses every delivery
  // slowSeconds, slowExemptPaths: a request at least this slow is kept, except when its path
  // contains
  // one of these texts
  // maxPerIpMinute: events kept per client address each minute, the rest are counted
  public record Gateway(
      String ingestKey, int slowSeconds, List<String> slowExemptPaths, int maxPerIpMinute) {}

  // failuresBeforeDown: consecutive failed probes before a service counts as down
  public record Probe(int failuresBeforeDown, int timeoutMs, List<Target> targets) {

    // url "jdbc" probes the monitor's own database; bodyContains, when set, must appear in the
    // answer; required false: a service that never answered is not reported as down
    public record Target(String name, String url, String bodyContains, boolean required) {}
  }

  public record Alert(
      int serverErrorCount,
      int serverErrorWindowMinutes,
      int rateLimitedCount,
      int rateLimitedWindowMinutes,
      int loginFailedCount,
      int loginFailedWindowMinutes,
      int repeatMinutes) {}

  public record KeycloakDb(String url, String username, String password) {}

  // adminUrl: webhook of the administrators' channel, else url; no webhook at all means no alerts
  public record ChatWebhook(String url, String adminUrl, int connectTimeoutMs, int readTimeoutMs) {}
}
