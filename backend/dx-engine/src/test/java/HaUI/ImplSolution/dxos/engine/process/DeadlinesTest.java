// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.ModelParser;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DeadlinesTest {

  private static String model(String process) {
    return """
        format: 1
        name: shop
        calendars:
          office:
            days: [mon, tue, wed, thu, fri]
            hours: ['08:00-12:00', '13:30-17:30']
        roles:
          - name: A
        entities:
          item:
            fields:
              a: {type: text}
            access: {A: all}
        """
        + "    process: "
        + process
        + "\n";
  }

  private static Instant at(int month, int day, int hour, int minute) {
    return LocalDateTime.of(2026, month, day, hour, minute).atZone(BusinessTime.ZONE).toInstant();
  }

  private static Timestamp dueNow(String process, Instant now) {
    Model model = ModelParser.parse(model(process));
    return new Deadlines(Clock.fixed(now, BusinessTime.ZONE))
        .dueNow(model, model.entities().get("item").process());
  }

  @Test
  void aProcessWithoutACalendarLeavesTheDeadlineToTheDatabase() {
    assertNull(dueNow("{family: approval, handler: A, deadline: 8h}", at(11, 23, 9, 0)));
  }

  @Test
  void aDeadlineInHoursCountsOnlyWorkingHours() {
    Timestamp due =
        dueNow(
            "{family: approval, handler: A, deadline: 8h, calendar: office}", at(11, 23, 16, 30));

    assertEquals(Timestamp.from(at(11, 24, 16, 30)), due);
  }

  @Test
  void aDeadlineInDaysCountsWorkingDays() {
    Timestamp due =
        dueNow("{family: approval, handler: A, deadline: 2d, calendar: office}", at(11, 27, 10, 0));

    assertEquals(Timestamp.from(at(12, 1, 10, 0)), due);
  }
}
