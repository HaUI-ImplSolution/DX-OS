// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// One live reading for a rounded position and hour. Rows are only added; BI aggregates them per
// day (vw_weather_daily).
@Entity
@Table(name = "weather_observations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeatherObservation {

  // Primary key
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Position rounded to two decimals, the same cell the weather cache uses
  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal latitude;

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal longitude;

  // Start of the hour the reading belongs to
  @Column(nullable = false)
  private Instant observedAt;

  @Column(nullable = false)
  private double temperatureC;

  @Column(nullable = false)
  private double humidityPercent;

  @Column(nullable = false)
  private double windSpeedKmh;

  // WMO weather interpretation code
  @Column(nullable = false)
  private int weatherCode;

  // Forecast of the reading's own day, taken at the same time
  @Column(nullable = false)
  private double dayTempMaxC;

  @Column(nullable = false)
  private double dayTempMinC;

  @Column(nullable = false)
  private double dayPrecipitationMm;
}
