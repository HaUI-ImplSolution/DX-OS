// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.query;

import java.time.Duration;
import java.time.Instant;

// The time spans the screen offers.
public enum Window {
  LAST_HOUR("1h", Duration.ofHours(1)), LAST_DAY("24h", Duration.ofHours(24)), LAST_WEEK("7d",
      Duration.ofDays(7));

  private final String code;
  private final Duration length;

  Window(String code, Duration length) {
    this.code = code;
    this.length = length;
  }

  public static Window of(String code) {
    for (Window window : values()) {
      if (window.code.equals(code)) {
        return window;
      }
    }
    throw new IllegalArgumentException("window must be 1h, 24h or 7d");
  }

  public String code() {
    return code;
  }

  public Instant start() {
    return Instant.now().minus(length);
  }
}
