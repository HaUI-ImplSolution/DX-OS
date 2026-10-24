// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.gateway;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

// Keeps one client address from filling the table: it admits a number of events each minute and
// refuses the rest, which the caller counts on an event it kept.
@Component
public class IpMinuteLimiter {

  private static final long WINDOW_MILLIS = 60_000;
  private static final int PRUNE_ABOVE = 10_000;

  private final Map<String, Window> windows = new HashMap<>();

  public synchronized boolean admit(String key, int maxPerMinute, long nowMillis) {
    if (windows.size() > PRUNE_ABOVE) {
      windows.values().removeIf(window -> nowMillis - window.start >= WINDOW_MILLIS);
    }
    Window window = windows.computeIfAbsent(key, k -> new Window(nowMillis));
    if (nowMillis - window.start >= WINDOW_MILLIS) {
      window.start = nowMillis;
      window.admitted = 0;
    }
    if (window.admitted >= maxPerMinute) {
      return false;
    }
    window.admitted++;
    return true;
  }

  private static final class Window {

    private long start;
    private int admitted;

    private Window(long start) {
      this.start = start;
    }
  }
}
