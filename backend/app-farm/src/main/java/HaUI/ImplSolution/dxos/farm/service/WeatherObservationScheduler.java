// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Records the weather of every approved parcel position, one minute after start and then at the
// configured interval, on one instance at a time.
@Component
public class WeatherObservationScheduler {

  private static final Logger log = LoggerFactory.getLogger(WeatherObservationScheduler.class);

  private final WeatherObservationService weatherObservationService;
  private final JobLock jobLock;

  public WeatherObservationScheduler(WeatherObservationService weatherObservationService,
      JobLock jobLock) {
    this.weatherObservationService = weatherObservationService;
    this.jobLock = jobLock;
  }

  @Scheduled(initialDelay = 60_000, fixedDelayString = "${weather.collect-interval:1h}")
  public void run() {
    try {
      jobLock.runIfFree("farm-weather-collect",
          () -> log.info("Weather readings stored: {}", weatherObservationService.collect()));
    } catch (Exception e) {
      log.error("Weather collect job failed", e);
    }
  }
}
