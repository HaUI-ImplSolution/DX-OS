// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.model.SeasonType;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Suggests the season type from the month a crop season starts. The months are configuration
// (app.season-start-months) and are not yet confirmed by an agronomist.
@Component
public class SeasonCalendar {

  private final Map<SeasonType, List<Integer>> startMonths = new EnumMap<>(SeasonType.class);

  public SeasonCalendar(@Value("${app.season-start-months.dong-xuan}") List<Integer> dongXuan,
      @Value("${app.season-start-months.he-thu}") List<Integer> heThu,
      @Value("${app.season-start-months.thu-dong}") List<Integer> thuDong) {
    startMonths.put(SeasonType.DONG_XUAN, dongXuan);
    startMonths.put(SeasonType.HE_THU, heThu);
    startMonths.put(SeasonType.THU_DONG, thuDong);
  }

  public Optional<SeasonType> suggest(LocalDate startDate) {
    int month = startDate.getMonthValue();
    return startMonths.entrySet().stream().filter(entry -> entry.getValue().contains(month))
        .map(Map.Entry::getKey).findFirst();
  }
}
