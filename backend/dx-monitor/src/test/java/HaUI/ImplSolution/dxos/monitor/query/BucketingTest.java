// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BucketingTest {

  @Test
  void theColumnsGetWiderWithTheSpan() {
    assertEquals(new Bucketing(60, "1 minute"), Bucketing.forSpan(Duration.ofMinutes(30)));
    assertEquals(new Bucketing(60, "1 minute"), Bucketing.forSpan(Duration.ofHours(1)));
    assertEquals(
        new Bucketing(300, "5 minutes"), Bucketing.forSpan(Duration.ofHours(1).plusSeconds(1)));
    assertEquals(new Bucketing(300, "5 minutes"), Bucketing.forSpan(Duration.ofHours(6)));
    assertEquals(new Bucketing(3600, "1 hour"), Bucketing.forSpan(Duration.ofHours(24)));
    assertEquals(new Bucketing(3600, "1 hour"), Bucketing.forSpan(Duration.ofHours(48)));
    assertEquals(new Bucketing(21600, "6 hours"), Bucketing.forSpan(Duration.ofDays(7)));
  }

  @Test
  void aColumnStartsOnTheHourOfTheBusinessDay() {
    Bucketing hourly = Bucketing.forSpan(Duration.ofHours(24));

    // 10:45 in Ho Chi Minh City is 03:45 UTC; the column starts at 10:00 there, 03:00 UTC.
    assertEquals(
        Instant.parse("2026-11-24T03:00:00Z"), hourly.start(Instant.parse("2026-11-24T03:45:10Z")));
  }

  @Test
  void aSixHourColumnStartsAtLocalMidnightNoonSixAndEighteen() {
    Bucketing wide = Bucketing.forSpan(Duration.ofDays(7));

    // 05:00 local is 22:00 UTC the day before; its column starts at 00:00 local, 17:00 UTC.
    assertEquals(
        Instant.parse("2026-11-23T17:00:00Z"), wide.start(Instant.parse("2026-11-23T22:00:00Z")));
    // 13:00 local, 06:00 UTC: the column of 12:00 local, 05:00 UTC.
    assertEquals(
        Instant.parse("2026-11-24T05:00:00Z"), wide.start(Instant.parse("2026-11-24T06:00:00Z")));
  }

  @Test
  void theStartOfAColumnStaysTheSameWithinIt() {
    Bucketing minutes = Bucketing.forSpan(Duration.ofMinutes(10));
    Instant start = Instant.parse("2026-11-24T03:07:00Z");

    assertEquals(start, minutes.start(start));
    assertEquals(start, minutes.start(start.plusSeconds(59)));
    assertEquals(start.plusSeconds(60), minutes.start(start.plusSeconds(60)));
  }

  @Test
  void anInstantBeforeTheEpochStillFindsItsColumn() {
    Bucketing hourly = Bucketing.forSpan(Duration.ofHours(24));

    assertEquals(
        Instant.parse("1969-12-31T23:00:00Z"), hourly.start(Instant.parse("1969-12-31T23:30:00Z")));
  }

  @Test
  void aWindowIsKnownByItsCode() {
    assertEquals(Window.LAST_DAY, Window.of("24h"));
    assertEquals("7d", Window.LAST_WEEK.code());
    assertThrows(IllegalArgumentException.class, () -> Window.of("3h"));
  }

  @Test
  void aWindowStartsItsLengthAgo() {
    Instant before = Instant.now();
    Instant start = Window.LAST_DAY.start();

    assertEquals(
        true,
        !start.isAfter(before.minus(Duration.ofHours(24)).plusSeconds(5))
            && start.isAfter(before.minus(Duration.ofHours(24)).minusSeconds(5)));
  }
}
