// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.farm.dto.ParcelResponse;
import HaUI.ImplSolution.dxos.farm.dto.WeatherResponse;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Weather at a parcel from Open-Meteo (no API key). Results are cached per rounded position; when
// the service cannot be reached the closest stored reference point is returned and marked.
@Service
public class WeatherService {

  private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

  private static final String SOURCE = "Open-Meteo (https://open-meteo.com), CC BY 4.0";
  private static final String SNAPSHOT_RESOURCE = "weather-snapshot.json";
  private static final String QUERY = "/v1/forecast?latitude={lat}&longitude={lon}"
      + "&current=temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m,weather_code"
      + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,"
      + "precipitation_probability_max&timezone=Asia/Ho_Chi_Minh&forecast_days=5";

  private final ParcelService parcelService;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final RestClient restClient;
  private final Duration cacheTtl;
  private final Map<String, CachedWeather> cache = new ConcurrentHashMap<>();
  private JsonNode snapshot;

  public WeatherService(ParcelService parcelService, ObjectMapper objectMapper, Clock clock,
      @Value("${weather.base-url}") String baseUrl,
      @Value("${weather.connect-timeout-ms}") int connectTimeoutMs,
      @Value("${weather.read-timeout-ms}") int readTimeoutMs,
      @Value("${weather.cache-ttl}") Duration cacheTtl) {
    this.parcelService = parcelService;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.cacheTtl = cacheTtl;
    HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }

  @PostConstruct
  void loadSnapshot() throws IOException {
    try (InputStream in = new ClassPathResource(SNAPSHOT_RESOURCE).getInputStream()) {
      snapshot = objectMapper.readTree(in);
    }
  }

  // Access follows the parcel: a farmer only reaches the weather of their own parcels.
  public WeatherResponse forParcel(String parcelCode) {
    ParcelResponse parcel = parcelService.getByCode(parcelCode);
    if (parcel.latitude() == null || parcel.longitude() == null) {
      throw new PokaYokeViolationException("PARCEL_LOCATION_MISSING",
          "Parcel " + parcelCode + " has no GPS position, so no weather can be shown");
    }
    double lat = parcel.latitude().doubleValue();
    double lon = parcel.longitude().doubleValue();
    String key = "%.2f,%.2f".formatted(lat, lon);
    CachedWeather cached = cache.get(key);
    Instant now = clock.instant();
    if (cached != null && cached.expiresAt().isAfter(now)) {
      return cached.forParcel(parcelCode, lat, lon);
    }
    try {
      WeatherResponse live = fetchLive(parcelCode, lat, lon, now);
      cache.put(key, new CachedWeather(live, now.plus(cacheTtl)));
      return live;
    } catch (RuntimeException e) {
      log.warn("Open-Meteo unavailable, serving the stored snapshot: {}", e.getMessage());
      return fromSnapshot(parcelCode, lat, lon);
    }
  }

  // Live reading for a position, never the snapshot; throws when Open-Meteo cannot be reached.
  public WeatherResponse liveAt(double lat, double lon) {
    return fetchLive(null, lat, lon, clock.instant());
  }

  private WeatherResponse fetchLive(String parcelCode, double lat, double lon, Instant now) {
    JsonNode body = restClient.get().uri(QUERY, lat, lon).retrieve().body(JsonNode.class);
    if (body == null || body.path("current").isMissingNode()
        || body.path("daily").path("time").isMissingNode()) {
      throw new IllegalStateException("Unexpected Open-Meteo response");
    }
    JsonNode current = body.get("current");
    JsonNode daily = body.get("daily");
    var currentWeather = new WeatherResponse.Current(current.get("temperature_2m").asDouble(),
        current.get("relative_humidity_2m").asDouble(), current.get("precipitation").asDouble(),
        current.get("wind_speed_10m").asDouble(), current.get("weather_code").asInt());
    List<WeatherResponse.Day> days = new ArrayList<>();
    for (int i = 0; i < daily.get("time").size(); i++) {
      days.add(new WeatherResponse.Day(daily.get("time").get(i).asText(),
          daily.get("temperature_2m_max").get(i).asDouble(),
          daily.get("temperature_2m_min").get(i).asDouble(),
          daily.get("precipitation_sum").get(i).asDouble(),
          daily.get("precipitation_probability_max").get(i).asInt()));
    }
    return new WeatherResponse(parcelCode, lat, lon, false, SOURCE, now, null, currentWeather,
        days);
  }

  private WeatherResponse fromSnapshot(String parcelCode, double lat, double lon) {
    JsonNode nearest = null;
    double best = Double.MAX_VALUE;
    for (JsonNode point : snapshot.get("points")) {
      double distance = Math.hypot(point.get("latitude").asDouble() - lat,
          (point.get("longitude").asDouble() - lon) * Math.cos(Math.toRadians(lat)));
      if (distance < best) {
        best = distance;
        nearest = point;
      }
    }
    JsonNode current = nearest.get("current");
    var currentWeather = new WeatherResponse.Current(current.get("temperatureC").asDouble(),
        current.get("humidityPercent").asDouble(), current.get("precipitationMm").asDouble(),
        current.get("windSpeedKmh").asDouble(), current.get("weatherCode").asInt());
    List<WeatherResponse.Day> days = new ArrayList<>();
    for (JsonNode day : nearest.get("daily")) {
      days.add(new WeatherResponse.Day(day.get("date").asText(), day.get("tempMaxC").asDouble(),
          day.get("tempMinC").asDouble(), day.get("precipitationMm").asDouble(),
          day.get("precipitationProbabilityPercent").asInt()));
    }
    String capturedAt = snapshot.get("capturedAt").asText();
    String note = "Backup data, not real time: stored reference point %s captured on %s."
        .formatted(nearest.get("name").asText(), capturedAt);
    Instant captured = LocalDate.parse(capturedAt).atStartOfDay(clock.getZone()).toInstant();
    return new WeatherResponse(parcelCode, lat, lon, true,
        snapshot.get("source").asText() + ", " + snapshot.get("license").asText(), captured, note,
        currentWeather, days);
  }

  private record CachedWeather(WeatherResponse weather, Instant expiresAt) {

    WeatherResponse forParcel(String parcelCode, double lat, double lon) {
      return new WeatherResponse(parcelCode, lat, lon, weather.isSnapshot(), weather.source(),
          weather.fetchedAt(), weather.note(), weather.current(), weather.daily());
    }
  }
}
