// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import static org.assertj.core.api.Assertions.assertThat;

import HaUI.ImplSolution.dxos.engine.apply.DdlGenerator;
import HaUI.ImplSolution.dxos.model.Escalation;
import HaUI.ImplSolution.dxos.model.Escalation.Slot;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.ModelParser;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Which notices fall due, that a sweep after a long stop sends only the latest one and never
// repeats one already sent, and that the records found overdue do not depend on the time zone.
@Testcontainers
class EscalationSweepTest {

  private static final String MODEL =
      """
      format: 0
      name: esctime
      roles:
        - name: STAFF
        - name: MANAGER
      entities:
        ticket:
          fields:
            title: {type: text}
          access: {STAFF: all, MANAGER: read}
          process:
            family: work_item
            handler: STAFF
            deadline: 72h
            escalate:
              - {after: 1h, to: MANAGER}
      """;

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static final List<Slot> SLOTS =
      Escalation.slots(
          List.of(
              new Escalation(Duration.ofHours(2), List.of("MANAGER"), Duration.ofHours(1), 3),
              new Escalation(Duration.ofHours(1), List.of("STAFF"), null, 1)));

  @Test
  void noticesFallDueInTimeOrderAcrossLevels() {
    assertThat(SLOTS)
        .extracting(Slot::level, Slot::round, slot -> slot.at().toHours())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(2, 0, 1L),
            org.assertj.core.groups.Tuple.tuple(1, 0, 2L),
            org.assertj.core.groups.Tuple.tuple(1, 1, 3L),
            org.assertj.core.groups.Tuple.tuple(1, 2, 4L));
  }

  @Test
  void aSweepAfterALongStopPicksOnlyTheLatestDueNotice() {
    assertThat(EscalationSweep.latestDue(SLOTS, Duration.ofMinutes(30).toSeconds())).isNull();
    assertThat(EscalationSweep.latestDue(SLOTS, Duration.ofMinutes(150).toSeconds()))
        .isEqualTo(new Slot(1, 0, Duration.ofHours(2)));
    assertThat(EscalationSweep.latestDue(SLOTS, Duration.ofDays(9).toSeconds()))
        .isEqualTo(new Slot(1, 2, Duration.ofHours(4)));
  }

  @Test
  void aNoticeAlreadySentIsNotSentAgain() {
    assertThat(EscalationSweep.sent(SLOTS, null)).isNegative();
    assertThat(EscalationSweep.sent(SLOTS, "9:9")).isNegative();
    assertThat(EscalationSweep.sent(SLOTS, "1:1")).isEqualTo(Duration.ofHours(3));
    // The sweep writes a notice only when it falls later than the last one sent.
    Slot due = EscalationSweep.latestDue(SLOTS, Duration.ofMinutes(190).toSeconds());
    assertThat(due.at().compareTo(EscalationSweep.sent(SLOTS, "1:1"))).isZero();
  }

  @Test
  void overdueRecordsAreTheSameInEveryTimeZone() {
    Model model = ModelParser.parse(MODEL);
    Entity ticket = model.entity("ticket").orElseThrow();
    DriverManagerDataSource plain =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    JdbcTemplate admin = new JdbcTemplate(plain);
    admin.execute("CREATE ROLE " + DdlGenerator.REPORT_ROLE);
    admin.execute(DdlGenerator.createSchema(model));
    admin.execute(
        "CREATE SCHEMA engine; CREATE TABLE engine.record_event (id BIGSERIAL, model TEXT,"
            + " entity TEXT, record_id BIGINT, action TEXT, detail TEXT,"
            + " at TIMESTAMPTZ NOT NULL DEFAULT now())");
    for (String zone :
        List.of("UTC", "Asia/Ho_Chi_Minh", "America/Los_Angeles", "Pacific/Kiritimati")) {
      SingleConnectionDataSource session =
          new SingleConnectionDataSource(
              POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), true);
      session.setSchema(model.schema());
      JdbcTemplate jdbc = new JdbcTemplate(session);
      jdbc.execute("SET TIME ZONE '" + zone + "'");
      if (zone.equals("UTC")) {
        jdbc.execute(DdlGenerator.desiredState(model, Map.of()));
        jdbc.execute(
            "INSERT INTO ticket (created_by, handle_by) VALUES ('t', now() - interval '5 hours'),"
                + " ('t', now() - interval '30 minutes')");
      }
      List<Map<String, Object>> rows =
          new EscalationSweep(jdbc, null, null, null, null, null)
              .unhandled(model, ticket, new Slot(1, 0, Duration.ofHours(1)));
      assertThat(rows).as(zone).hasSize(1);
      assertThat(((Number) rows.get(0).get("late")).doubleValue())
          .as(zone)
          .isBetween(17900.0, 18700.0);
      session.destroy();
    }
  }
}
