// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

// @formatter:off
public record WeatherResponse(
  String parcelCode,

  // Position the forecast was asked for
  double latitude,

  double longitude,

  // True when the live service was unreachable and stored reference data is shown instead
  @JsonProperty("isSnapshot") boolean isSnapshot,

  // Where the data comes from, with its license
  String source,

  // When the data was fetched (live) or captured (snapshot)
  Instant fetchedAt,

  // Set on a snapshot: which reference point it describes and that it is not real time
  String note,

  Current current,

  List<Day> daily
){
  public record Current(
    double temperatureC,
    double humidityPercent,
    double precipitationMm,
    double windSpeedKmh,
    // WMO weather interpretation code
    int weatherCode
  ){}

  public record Day(
    String date,
    double tempMaxC,
    double tempMinC,
    double precipitationMm,
    int precipitationProbabilityPercent
  ){}
}
