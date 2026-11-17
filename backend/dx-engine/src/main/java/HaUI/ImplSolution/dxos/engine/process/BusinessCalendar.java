// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import HaUI.ImplSolution.dxos.model.Model.Calendar;
import HaUI.ImplSolution.dxos.model.Model.TimeRange;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

// Counts a deadline in the working time of a calendar, in the time zone of the business day.
// A deadline of minutes or hours runs only through working hours.
// A deadline of days is that many working days, ending at the same time of day.
// A start outside the working hours counts from the next opening.
public final class BusinessCalendar {

  // A calendar always has a working day, so the search for one stops far beyond any holiday list.
  private static final int SEARCH_DAYS = 36_600;

  private final Calendar calendar;

  public BusinessCalendar(Calendar calendar) {
    this.calendar = calendar;
  }

  // The moment a deadline of the length counts out when it starts at from.
  public Instant due(Instant from, Duration length) {
    ZonedDateTime at = opening(from.atZone(BusinessTime.ZONE));
    Duration left = length;
    LocalDate day = at.toLocalDate();
    for (int i = 0; i < SEARCH_DAYS; i++, day = day.plusDays(1)) {
      if (!works(day)) {
        continue;
      }
      for (TimeRange span : calendar.hours()) {
        ZonedDateTime start = max(at, day.atTime(span.start()).atZone(BusinessTime.ZONE));
        ZonedDateTime end = end(day, span);
        if (!start.isBefore(end)) {
          continue;
        }
        Duration room = Duration.between(start, end);
        if (left.compareTo(room) <= 0) {
          return start.plus(left).toInstant();
        }
        left = left.minus(room);
      }
    }
    throw new IllegalStateException("no working time within " + SEARCH_DAYS + " days");
  }

  // The moment a deadline of the number of working days counts out, at the time of day of the
  // opening it starts from.
  public Instant dueInDays(Instant from, long days) {
    ZonedDateTime at = opening(from.atZone(BusinessTime.ZONE));
    LocalDate day = at.toLocalDate();
    long left = days;
    for (int i = 0; i < SEARCH_DAYS && left > 0; i++) {
      day = day.plusDays(1);
      if (works(day)) {
        left--;
      }
    }
    return day.atTime(at.toLocalTime()).atZone(BusinessTime.ZONE).toInstant();
  }

  // The first moment from at, or after it, that is inside working hours.
  private ZonedDateTime opening(ZonedDateTime from) {
    LocalDate day = from.toLocalDate();
    for (int i = 0; i < SEARCH_DAYS; i++, day = day.plusDays(1)) {
      if (!works(day)) {
        continue;
      }
      for (TimeRange span : calendar.hours()) {
        ZonedDateTime end = end(day, span);
        if (end.isAfter(from)) {
          return max(from, day.atTime(span.start()).atZone(BusinessTime.ZONE));
        }
      }
    }
    throw new IllegalStateException("no working time within " + SEARCH_DAYS + " days");
  }

  private boolean works(LocalDate day) {
    return calendar.days().contains(day.getDayOfWeek()) && !calendar.holidays().contains(day);
  }

  // An end of midnight is the end of the day.
  private static ZonedDateTime end(LocalDate day, TimeRange span) {
    return span.end().equals(LocalTime.MIDNIGHT)
        ? day.plusDays(1).atStartOfDay(BusinessTime.ZONE)
        : day.atTime(span.end()).atZone(BusinessTime.ZONE);
  }

  private static ZonedDateTime max(ZonedDateTime a, ZonedDateTime b) {
    return a.isAfter(b) ? a : b;
  }
}
