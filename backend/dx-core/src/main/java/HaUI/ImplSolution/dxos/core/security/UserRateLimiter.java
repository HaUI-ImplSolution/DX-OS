// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.security;

import java.util.HashMap;
import java.util.Map;

// Counts calls per key in one-minute windows kept in memory.
public class UserRateLimiter {

  private static final long WINDOW_MILLIS = 60_000;
  private static final int PRUNE_ABOVE = 10_000;

  private final Map<String, Window> windows = new HashMap<>();

  // 0 when the call is admitted, otherwise the seconds until the window resets.
  public synchronized long admit(String key, int maxPerMinute, long nowMillis) {
    if (windows.size() > PRUNE_ABOVE) {
      windows.values().removeIf(window -> nowMillis - window.start >= WINDOW_MILLIS);
    }
    Window window = windows.computeIfAbsent(key, k -> new Window(nowMillis));
    if (nowMillis - window.start >= WINDOW_MILLIS) {
      window.start = nowMillis;
      window.count = 0;
    }
    if (window.count >= maxPerMinute) {
      return Math.max(1, (window.start + WINDOW_MILLIS - nowMillis + 999) / 1000);
    }
    window.count++;
    return 0;
  }

  private static final class Window {

    private long start;
    private int count;

    private Window(long start) {
      this.start = start;
    }
  }
}
