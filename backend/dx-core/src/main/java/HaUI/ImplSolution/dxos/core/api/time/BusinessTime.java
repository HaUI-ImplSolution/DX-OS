// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.time;

import java.time.ZoneId;

// Time zone of the business day, independent of the container time zone.
public final class BusinessTime {

  public static final String ZONE_ID = "Asia/Ho_Chi_Minh";
  public static final ZoneId ZONE = ZoneId.of(ZONE_ID);

  private BusinessTime() {}
}
