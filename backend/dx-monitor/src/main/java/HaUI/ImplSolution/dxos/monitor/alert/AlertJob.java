// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.alert;

import HaUI.ImplSolution.dxos.monitor.config.MonitorProperties;
import HaUI.ImplSolution.dxos.monitor.health.ServiceStateChanged;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Tells the administrators' chat channel about a service going down or coming back, a burst of
// 5xx answers, and a client address rate limited many times. The same condition is reported at
// most once per repeat interval.
@Component
public class AlertJob {

  private static final Logger log = LoggerFactory.getLogger(AlertJob.class);

  private final MonitorProperties.Alert settings;
  private final JdbcTemplate jdbc;
  private final MattermostSender sender;
  private final Map<String, Instant> reported = new HashMap<>();

  public AlertJob(MonitorProperties properties, JdbcTemplate jdbc, MattermostSender sender) {
    this.settings = properties.alert();
    this.jdbc = jdbc;
    this.sender = sender;
  }

  @EventListener
  public void onServiceChange(ServiceStateChanged change) {
    if (ServiceStateChanged.DOWN.equals(change.state())) {
      sender.send(":rotating_light: Dịch vụ %s ngừng hoạt động%s.".formatted(change.service(),
          change.detail() == null ? "" : " (" + change.detail() + ")"));
    } else if (ServiceStateChanged.DOWN.equals(change.previous())) {
      sender
          .send(":white_check_mark: Dịch vụ %s đã hoạt động trở lại.".formatted(change.service()));
    }
  }

  @Scheduled(cron = "${monitor.alert.cron:0 * * * * *}", zone = "Asia/Ho_Chi_Minh")
  public void run() {
    try {
      check();
    } catch (Exception e) {
      log.error("Alert check failed", e);
    }
  }

  void check() {
    if (!sender.isEnabled()) {
      return;
    }
    Instant now = Instant.now();
    Duration repeat = Duration.ofMinutes(settings.repeatMinutes());
    reported.values().removeIf(sentAt -> sentAt.plus(repeat).isBefore(now));

    long errors = count("kind IN ('SERVER_ERROR', 'UPSTREAM_DOWN')",
        now.minus(Duration.ofMinutes(settings.serverErrorWindowMinutes())));
    if (errors >= settings.serverErrorCount() && reported.putIfAbsent("5xx", now) == null) {
      sender.send(":warning: %d lỗi 5xx tại cổng trong %d phút qua. Xem màn hình giám sát."
          .formatted(errors, settings.serverErrorWindowMinutes()));
    }

    List<Object[]> limited = jdbc.query("""
        SELECT ip, count(*) + sum(suppressed) AS total FROM gateway_event
        WHERE kind = 'RATE_LIMITED' AND occurred_at >= ?
        GROUP BY ip HAVING count(*) + sum(suppressed) >= ?
        """, (rs, i) -> new Object[] {rs.getString("ip"), rs.getLong("total")},
        Timestamp.from(now.minus(Duration.ofMinutes(settings.rateLimitedWindowMinutes()))),
        settings.rateLimitedCount());
    for (Object[] row : limited) {
      if (reported.putIfAbsent("429|" + row[0], now) == null) {
        sender.send(":warning: Địa chỉ %s bị giới hạn tốc độ %d lần trong %d phút qua."
            .formatted(row[0], row[1], settings.rateLimitedWindowMinutes()));
      }
    }
  }

  private long count(String condition, Instant from) {
    Long total = jdbc.queryForObject("SELECT count(*) + coalesce(sum(suppressed), 0) "
        + "FROM gateway_event WHERE " + condition + " AND occurred_at >= ?", Long.class,
        Timestamp.from(from));
    return total == null ? 0 : total;
  }
}
