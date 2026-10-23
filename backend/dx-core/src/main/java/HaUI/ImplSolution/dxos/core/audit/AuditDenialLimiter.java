// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.audit;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Keeps one source from filling the audit table: per key it admits a number of records each
// minute and counts the rest, which are reported with the first record of the next minute.
@Component
public class AuditDenialLimiter {

  private static final long WINDOW_MILLIS = 60_000;
  private static final int PRUNE_ABOVE = 10_000;

  private final int maxPerMinute;
  private final Map<String, Window> windows = new HashMap<>();

  public AuditDenialLimiter(@Value("${audit.denied.max-per-minute:20}") int maxPerMinute) {
    this.maxPerMinute = maxPerMinute;
  }

  // -1 when the record must be dropped, otherwise the number of records dropped in the previous
  // minute.
  public synchronized int admit(String key, long nowMillis) {
    if (windows.size() > PRUNE_ABOVE) {
      windows.values().removeIf(window -> nowMillis - window.start >= WINDOW_MILLIS);
    }
    Window window = windows.computeIfAbsent(key, k -> new Window(nowMillis));
    int carried = 0;
    if (nowMillis - window.start >= WINDOW_MILLIS) {
      carried = window.dropped;
      window.start = nowMillis;
      window.admitted = 0;
      window.dropped = 0;
    }
    if (window.admitted >= maxPerMinute) {
      window.dropped++;
      return -1;
    }
    window.admitted++;
    return carried;
  }

  private static final class Window {

    private long start;
    private int admitted;
    private int dropped;

    private Window(long start) {
      this.start = start;
    }
  }
}
