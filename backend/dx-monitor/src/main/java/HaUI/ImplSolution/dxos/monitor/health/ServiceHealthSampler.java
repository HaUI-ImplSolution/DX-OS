// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.health;

import HaUI.ImplSolution.dxos.monitor.config.MonitorProperties;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Probes every configured service and records the moments one goes down or comes back. A service
// counts as down after several failed probes in a row, so one slow answer is not reported.
@Component
public class ServiceHealthSampler {

  private static final Logger log = LoggerFactory.getLogger(ServiceHealthSampler.class);

  private final MonitorProperties.Probe settings;
  private final ServiceProbe probe;
  private final ServiceHealthStore store;
  private final ApplicationEventPublisher events;
  private final Map<String, String> known = new HashMap<>();
  private final Map<String, Integer> failures = new HashMap<>();
  private boolean loaded;

  public ServiceHealthSampler(MonitorProperties properties, ServiceProbe probe,
      ServiceHealthStore store, ApplicationEventPublisher events) {
    this.settings = properties.probe();
    this.probe = probe;
    this.store = store;
    this.events = events;
  }

  @Scheduled(fixedDelayString = "${monitor.probe.interval-ms:30000}",
      initialDelayString = "${monitor.probe.initial-delay-ms:5000}")
  public void run() {
    try {
      sample();
    } catch (Exception e) {
      log.error("Service probe round failed", e);
    }
  }

  void sample() {
    if (!loaded) {
      known.putAll(store.lastStates());
      loaded = true;
    }
    for (MonitorProperties.Probe.Target target : settings.targets()) {
      ServiceProbe.Result result = probe.probe(target);
      String previous = known.get(target.name());
      if (result.up()) {
        failures.remove(target.name());
        change(target.name(), ServiceStateChanged.UP, previous, null);
        continue;
      }
      int count = failures.merge(target.name(), 1, Integer::sum);
      boolean seen = previous != null || target.required();
      if (seen && count >= settings.failuresBeforeDown()) {
        change(target.name(), ServiceStateChanged.DOWN, previous, result.detail());
      }
    }
  }

  private void change(String service, String state, String previous, String detail) {
    if (state.equals(previous)) {
      return;
    }
    Instant now = Instant.now();
    store.record(service, state, detail, now);
    known.put(service, state);
    events.publishEvent(new ServiceStateChanged(service, state, previous, detail, now));
  }
}
