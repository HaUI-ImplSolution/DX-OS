// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.farm.dto.WeatherResponse;
import HaUI.ImplSolution.dxos.farm.model.ApprovalStatus;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import HaUI.ImplSolution.dxos.farm.model.WeatherObservation;
import HaUI.ImplSolution.dxos.farm.repository.ParcelRepository;
import HaUI.ImplSolution.dxos.farm.repository.WeatherObservationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Stores one live reading per approved parcel position and hour so BI can chart the weather over
// time. A position Open-Meteo cannot serve is skipped: the stored snapshot is never recorded.
@Service
public class WeatherObservationService {

  private static final Logger log = LoggerFactory.getLogger(WeatherObservationService.class);

  private record Cell(BigDecimal latitude, BigDecimal longitude) {}

  private final ParcelRepository parcelRepository;
  private final WeatherObservationRepository observationRepository;
  private final WeatherService weatherService;
  private final Clock clock;

  public WeatherObservationService(ParcelRepository parcelRepository,
      WeatherObservationRepository observationRepository, WeatherService weatherService,
      Clock clock) {
    this.parcelRepository = parcelRepository;
    this.observationRepository = observationRepository;
    this.weatherService = weatherService;
    this.clock = clock;
  }

  // Returns how many readings were stored.
  public int collect() {
    Instant hour = clock.instant().truncatedTo(ChronoUnit.HOURS);
    int stored = 0;
    for (Cell cell : cells()) {
      if (observationRepository.existsByLatitudeAndLongitudeAndObservedAt(cell.latitude(),
          cell.longitude(), hour)) {
        continue;
      }
      try {
        WeatherResponse live =
            weatherService.liveAt(cell.latitude().doubleValue(), cell.longitude().doubleValue());
        WeatherResponse.Day today = live.daily().get(0);
        observationRepository
            .save(WeatherObservation.builder().latitude(cell.latitude()).longitude(cell.longitude())
                .observedAt(hour).temperatureC(live.current().temperatureC())
                .humidityPercent(live.current().humidityPercent())
                .windSpeedKmh(live.current().windSpeedKmh())
                .weatherCode(live.current().weatherCode()).dayTempMaxC(today.tempMaxC())
                .dayTempMinC(today.tempMinC()).dayPrecipitationMm(today.precipitationMm()).build());
        stored++;
      } catch (RuntimeException e) {
        log.warn("Weather reading skipped for {},{}: {}", cell.latitude(), cell.longitude(),
            e.getMessage());
      }
    }
    return stored;
  }

  private Set<Cell> cells() {
    Set<Cell> cells = new LinkedHashSet<>();
    for (Parcel parcel : parcelRepository.findByDeletedAtIsNullOrderByIdAsc()) {
      if (parcel.getApprovalStatus() == ApprovalStatus.APPROVED && parcel.getLatitude() != null
          && parcel.getLongitude() != null) {
        cells.add(new Cell(parcel.getLatitude().setScale(2, RoundingMode.HALF_UP),
            parcel.getLongitude().setScale(2, RoundingMode.HALF_UP)));
      }
    }
    return cells;
  }
}
