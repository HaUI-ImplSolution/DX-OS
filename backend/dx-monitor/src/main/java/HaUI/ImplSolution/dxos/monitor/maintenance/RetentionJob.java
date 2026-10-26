// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.maintenance;

import HaUI.ImplSolution.dxos.monitor.config.MonitorProperties;
import HaUI.ImplSolution.dxos.monitor.gateway.GatewayEventStore;
import HaUI.ImplSolution.dxos.monitor.health.ServiceHealthStore;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Deletes the gateway events and service state changes older than the retention period.
@Component
public class RetentionJob {

  private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

  private final GatewayEventStore gatewayEvents;
  private final ServiceHealthStore healthEvents;
  private final Duration retention;

  public RetentionJob(GatewayEventStore gatewayEvents, ServiceHealthStore healthEvents,
      MonitorProperties properties) {
    this.gatewayEvents = gatewayEvents;
    this.healthEvents = healthEvents;
    this.retention = Duration.ofDays(properties.retentionDays());
  }

  @Scheduled(cron = "${monitor.retention-cron:0 30 3 * * *}", zone = "Asia/Ho_Chi_Minh")
  public void run() {
    try {
      int gateway = gatewayEvents.deleteOlderThan(retention);
      int health = healthEvents.deleteOlderThan(retention);
      log.info("Deleted {} gateway events and {} service state changes", gateway, health);
    } catch (Exception e) {
      log.error("Retention job failed", e);
    }
  }
}
