// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.exception.PokaYokeViolationException;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// A farmer's GPS fix must be accurate and not faked; an admin entering a location by hand is
// exempt because there is no fix to measure.
@Component
public class GpsFixValidator {

  private final BigDecimal maxAccuracyMeters;

  public GpsFixValidator(@Value("${app.gps-max-accuracy-meters:30}") BigDecimal maxAccuracyMeters) {
    this.maxAccuracyMeters = maxAccuracyMeters;
  }

  public void requireTrusted(BigDecimal accuracyMeters, Boolean mocked) {
    if (Boolean.TRUE.equals(mocked)) {
      throw new PokaYokeViolationException("GPS_MOCKED",
          "Vị trí có dấu hiệu giả lập, hãy tắt ứng dụng giả vị trí rồi lấy lại");
    }
    if (accuracyMeters == null) {
      throw new PokaYokeViolationException("GPS_ACCURACY_MISSING",
          "Thiếu sai số GPS, hãy lấy vị trí ngay tại ruộng");
    }
    if (accuracyMeters.compareTo(maxAccuracyMeters) > 0) {
      throw new PokaYokeViolationException("GPS_ACCURACY_TOO_LOW",
          "Sai số GPS %s m vượt ngưỡng %s m, hãy ra chỗ thoáng rồi lấy lại".formatted(
              accuracyMeters.stripTrailingZeros().toPlainString(),
              maxAccuracyMeters.stripTrailingZeros().toPlainString()));
    }
  }
}
