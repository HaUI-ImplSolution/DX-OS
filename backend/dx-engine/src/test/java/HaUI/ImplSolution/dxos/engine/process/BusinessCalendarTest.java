// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import HaUI.ImplSolution.dxos.model.Model.Calendar;
import HaUI.ImplSolution.dxos.model.Model.TimeRange;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

// 23/11/2026 is a Monday, 27/11 a Friday, 28/11 a Saturday and 25/12 a Friday that is a holiday.
class BusinessCalendarTest {

  private static final Set<DayOfWeek> WEEK =
      Set.of(
          DayOfWeek.MONDAY,
          DayOfWeek.TUESDAY,
          DayOfWeek.WEDNESDAY,
          DayOfWeek.THURSDAY,
          DayOfWeek.FRIDAY);

  private static final BusinessCalendar OFFICE =
      new BusinessCalendar(
          new Calendar(
              WEEK,
              List.of(
                  new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0)),
                  new TimeRange(LocalTime.of(13, 30), LocalTime.of(17, 30))),
              Set.of(LocalDate.of(2026, 12, 25))));

  private static Instant at(int month, int day, int hour, int minute) {
    return LocalDateTime.of(2026, month, day, hour, minute).atZone(BusinessTime.ZONE).toInstant();
  }

  private static Instant due(Instant from, long hours) {
    return OFFICE.due(from, Duration.ofHours(hours));
  }

  @Test
  void aDeadlineInsideTheWorkingHoursEndsAtTheSameDay() {
    assertEquals(at(11, 23, 11, 0), due(at(11, 23, 9, 0), 2));
  }

  @Test
  void aDeadlineSkipsTheLunchBreak() {
    assertEquals(at(11, 23, 14, 30), due(at(11, 23, 9, 0), 4));
  }

  @Test
  void aDeadlineThatEndsExactlyWhenTheDayClosesEndsThen() {
    assertEquals(at(11, 23, 17, 30), due(at(11, 23, 16, 30), 1));
  }

  @Test
  void aDeadlineRunsOnIntoTheNextWorkingDay() {
    assertEquals(at(11, 24, 10, 0), due(at(11, 23, 16, 30), 3));
  }

  @Test
  void aDeadlineThatStartsOnAFridayEveningResumesOnMonday() {
    assertEquals(at(11, 30, 9, 0), due(at(11, 27, 16, 30), 2));
  }

  @Test
  void aStartOutsideTheWorkingHoursCountsFromTheNextOpening() {
    assertEquals(at(11, 23, 9, 0), due(at(11, 23, 6, 0), 1), "before the opening");
    assertEquals(at(11, 23, 14, 30), due(at(11, 23, 12, 30), 1), "at lunch");
    assertEquals(at(11, 30, 9, 0), due(at(11, 28, 10, 0), 1), "on a Saturday");
    assertEquals(at(11, 24, 9, 0), due(at(11, 23, 20, 0), 1), "after the closing");
  }

  @Test
  void aHolidayAndTheWeekendAreSkipped() {
    assertEquals(at(12, 28, 9, 0), due(at(12, 24, 16, 30), 2));
  }

  @Test
  void aDeadlineOfNothingIsDueAtTheOpening() {
    assertEquals(at(11, 23, 13, 30), OFFICE.due(at(11, 23, 12, 30), Duration.ZERO));
  }

  @Test
  void aDeadlineOfMinutesCountsInMinutes() {
    assertEquals(at(11, 23, 12, 0), OFFICE.due(at(11, 23, 11, 30), Duration.ofMinutes(30)));
    assertEquals(at(11, 23, 13, 45), OFFICE.due(at(11, 23, 11, 45), Duration.ofMinutes(30)));
  }

  @Test
  void aSpanThatEndsAtMidnightRunsToTheEndOfTheDay() {
    BusinessCalendar night =
        new BusinessCalendar(
            new Calendar(
                WEEK, List.of(new TimeRange(LocalTime.of(20, 0), LocalTime.MIDNIGHT)), Set.of()));

    assertEquals(at(11, 23, 23, 30), night.due(at(11, 23, 21, 0), Duration.ofMinutes(150)));
    assertEquals(at(11, 24, 22, 0), night.due(at(11, 23, 21, 0), Duration.ofHours(5)));
  }

  @Test
  void aDeadlineInDaysCountsWorkingDaysAtTheSameTimeOfDay() {
    assertEquals(at(11, 24, 10, 0), OFFICE.dueInDays(at(11, 23, 10, 0), 1));
    assertEquals(at(12, 2, 10, 0), OFFICE.dueInDays(at(11, 27, 10, 0), 3));
    assertEquals(at(11, 30, 10, 0), OFFICE.dueInDays(at(11, 27, 10, 0), 1));
  }

  @Test
  void aDeadlineInDaysSkipsHolidaysAndStartsFromTheOpening() {
    assertEquals(at(12, 28, 9, 0), OFFICE.dueInDays(at(12, 24, 9, 0), 1));
    assertEquals(at(12, 1, 8, 0), OFFICE.dueInDays(at(11, 28, 10, 0), 1));
    assertEquals(at(11, 23, 8, 0), OFFICE.dueInDays(at(11, 22, 10, 0), 0));
  }

  @Test
  void aCalendarWithoutWorkingDaysFindsNoDeadline() {
    BusinessCalendar never =
        new BusinessCalendar(
            new Calendar(
                Set.of(),
                List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0))),
                Set.of()));

    assertThrows(
        IllegalStateException.class, () -> never.due(at(11, 23, 9, 0), Duration.ofHours(1)));
  }
}
