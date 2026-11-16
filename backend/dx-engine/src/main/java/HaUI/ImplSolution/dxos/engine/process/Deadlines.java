// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Process;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

// The moment a record that starts waiting for a handler is due, when its process counts the
// deadline in a working calendar.
@Component
public class Deadlines {

  private final Clock clock;

  public Deadlines(Clock clock) {
    this.clock = clock;
  }

  // The due moment of a record that starts waiting now, or null when the process has no calendar
  // and the database adds the deadline to its own clock.
  public Timestamp dueNow(Model model, Process process) {
    if (process.calendar() == null) {
      return null;
    }
    BusinessCalendar calendar = new BusinessCalendar(model.calendars().get(process.calendar()));
    Instant now = clock.instant();
    Instant due =
        process.deadlineInDays()
            ? calendar.dueInDays(now, process.deadline().toDays())
            : calendar.due(now, process.deadline());
    return Timestamp.from(due);
  }
}
