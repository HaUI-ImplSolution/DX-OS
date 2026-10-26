// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.gateway;

import HaUI.ImplSolution.dxos.monitor.config.MonitorProperties;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

// Turns a log entry sent by the gateway into an event, or drops it when it is ordinary traffic.
@Component
public class GatewayEventFilter {

  private static final String LOOPBACK = "127.0.0.1";

  private final MonitorProperties.Gateway settings;

  public GatewayEventFilter(MonitorProperties properties) {
    this.settings = properties.gateway();
  }

  public Optional<GatewayEvent> classify(Map<String, Object> entry) {
    Integer status = number(entry.get("status"));
    String ip = text(entry.get("remote_addr"), 64);
    if (status == null || ip == null || LOOPBACK.equals(ip) || "::1".equals(ip)) {
      return Optional.empty();
    }
    String path = text(entry.get("uri"), 512);
    // An expired session of the screen itself is not a rejected token.
    if (status == 401 && path != null && path.startsWith("/monitor/")) {
      return Optional.empty();
    }
    Double seconds = decimal(entry.get("request_time"));
    int requestTimeMs = seconds == null ? 0 : (int) Math.min(Integer.MAX_VALUE, seconds * 1000);
    GatewayEventKind kind = kindOf(status, requestTimeMs, path);
    if (kind == null) {
      return Optional.empty();
    }
    return Optional.of(new GatewayEvent(time(entry.get("time")), kind, ip,
        text(entry.get("host"), 255), text(entry.get("method"), 16), path, status, requestTimeMs,
        firstAddress(text(entry.get("upstream_addr"), 128)), text(entry.get("request_id"), 64),
        text(entry.get("cf_ray"), 64), text(entry.get("user_agent"), 255), 0));
  }

  private GatewayEventKind kindOf(int status, int requestTimeMs, String path) {
    if (status == 429) {
      return GatewayEventKind.RATE_LIMITED;
    }
    if (status == 401) {
      return GatewayEventKind.AUTH_REJECTED;
    }
    if (status == 404) {
      return GatewayEventKind.NOT_FOUND;
    }
    if (status == 502 || status == 503 || status == 504) {
      return GatewayEventKind.UPSTREAM_DOWN;
    }
    if (status >= 500) {
      return GatewayEventKind.SERVER_ERROR;
    }
    if (requestTimeMs >= settings.slowSeconds() * 1000L && !exempt(path)) {
      return GatewayEventKind.SLOW;
    }
    return null;
  }

  private boolean exempt(String path) {
    return path != null && settings.slowExemptPaths().stream().anyMatch(path::contains);
  }

  private static Instant time(Object value) {
    try {
      return value == null ? Instant.now() : OffsetDateTime.parse(value.toString()).toInstant();
    } catch (DateTimeParseException e) {
      return Instant.now();
    }
  }

  // "10.0.0.5:8081, 10.0.0.6:8081" or "-" when the gateway answered itself.
  private static String firstAddress(String upstream) {
    if (upstream == null || "-".equals(upstream)) {
      return null;
    }
    int comma = upstream.indexOf(',');
    return comma < 0 ? upstream : upstream.substring(0, comma).strip();
  }

  private static String text(Object value, int max) {
    if (value == null) {
      return null;
    }
    String text = value.toString();
    if (text.isBlank() || "-".equals(text)) {
      return null;
    }
    return text.length() <= max ? text : text.substring(0, max);
  }

  private static Integer number(Object value) {
    Double parsed = decimal(value);
    return parsed == null ? null : parsed.intValue();
  }

  private static Double decimal(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return value == null ? null : Double.valueOf(value.toString().strip());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
