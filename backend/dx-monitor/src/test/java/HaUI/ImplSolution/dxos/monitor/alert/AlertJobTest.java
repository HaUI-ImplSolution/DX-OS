// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.alert;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.monitor.config.MonitorProperties;
import HaUI.ImplSolution.dxos.monitor.query.AuthEventReader;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AlertJobTest {

  private final MattermostSender sender = mock(MattermostSender.class);
  private final AuthEventReader authEvents = mock(AuthEventReader.class);
  private final Instant now = Instant.parse("2026-10-28T08:00:00Z");

  private AlertJob job() {
    MonitorProperties properties = mock(MonitorProperties.class);
    when(properties.alert()).thenReturn(new MonitorProperties.Alert(5, 5, 10, 10, 10, 10, 60));
    return new AlertJob(properties, mock(JdbcTemplate.class), sender, authEvents);
  }

  @Test
  void manyFailedSignInsFromOneAddressAreReportedOnceUntilTheRepeatInterval() {
    when(authEvents.failedPerIp(any(), anyInt()))
        .thenReturn(Optional.of(Map.of("203.0.113.5", 12L)));
    AlertJob job = job();

    job.checkFailedLogins(now);
    job.checkFailedLogins(now.plusSeconds(60));

    verify(sender, times(1)).send(contains("12 lần đăng nhập sai từ địa chỉ 203.0.113.5"));
  }

  @Test
  void aFewFailuresSendNothing() {
    when(authEvents.failedPerIp(any(), anyInt()))
        .thenReturn(Optional.of(Map.of("203.0.113.5", 3L)));

    job().checkFailedLogins(now);

    verify(sender, never()).send(any());
  }

  @Test
  void unreadableKeycloakEventsSendNothing() {
    when(authEvents.failedPerIp(any(), anyInt())).thenReturn(Optional.empty());

    job().checkFailedLogins(now);

    verify(sender, never()).send(any());
  }
}
