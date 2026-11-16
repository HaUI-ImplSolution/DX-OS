// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import static org.assertj.core.api.Assertions.assertThat;

import HaUI.ImplSolution.dxos.engine.apply.DdlGenerator;
import HaUI.ImplSolution.dxos.engine.apply.Sql;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.ModelParser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// The figures are computed by the database over the rows given, so they run on a real one.
@Testcontainers
class ReportServiceTest {

  private static final String MODEL =
      """
      format: 0
      name: report
      roles:
        - name: STAFF
      entities:
        item:
          fields:
            kind: {type: choice, options: [a, b, c]}
            qty: {type: number}
            due: {type: date}
          access: {STAFF: all}
        ticket:
          fields:
            kind: {type: choice, options: [a, b]}
          access: {STAFF: all}
          process: {family: approval, handler: STAFF}
      """;

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  static NamedParameterJdbcTemplate jdbc;
  static ReportService service;
  static Entity item;
  static Entity ticket;
  static String itemTable;
  static String ticketTable;

  @BeforeAll
  static void schema() {
    Model model = ModelParser.parse(MODEL);
    DriverManagerDataSource plain =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    JdbcTemplate admin = new JdbcTemplate(plain);
    admin.execute("CREATE ROLE " + DdlGenerator.REPORT_ROLE);
    admin.execute(DdlGenerator.createSchema(model));
    // The tables are created without a schema, so every connection starts in the one of the model.
    DriverManagerDataSource inSchema =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    inSchema.setSchema(model.schema());
    jdbc = new NamedParameterJdbcTemplate(inSchema);
    jdbc.getJdbcOperations().execute(DdlGenerator.desiredState(model, Map.of()));
    item = model.entity("item").orElseThrow();
    ticket = model.entity("ticket").orElseThrow();
    itemTable = Sql.table(model, item);
    ticketTable = Sql.table(model, ticket);
    service = new ReportService(null, jdbc);
  }

  @BeforeEach
  void empty() {
    jdbc.getJdbcOperations().execute("TRUNCATE " + itemTable + ", " + ticketTable);
  }

  private static void item(String owner, String kind, String qty, String due) {
    jdbc.update(
        "INSERT INTO "
            + itemTable
            + " (created_by, kind, qty, due) VALUES (:owner, :kind, CAST(:qty AS numeric),"
            + " CAST(:due AS date))",
        new MapSqlParameterSource("owner", owner)
            .addValue("kind", kind)
            .addValue("qty", qty)
            .addValue("due", due));
  }

  private static Map<String, Object> run(
      Entity entity, String table, String where, String... pairs) {
    Map<String, String> params = new java.util.HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      params.put(pairs[i], pairs[i + 1]);
    }
    DataService.Rows rows =
        new DataService.Rows(" FROM " + table + " WHERE " + where, new MapSqlParameterSource());
    return service.run(ReportQuery.parse(entity, params), rows);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> groups(Map<String, Object> report) {
    return (List<Map<String, Object>>) report.get("groups");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> totals(Map<String, Object> report) {
    return (Map<String, Object>) report.get("totals");
  }

  @Test
  void groupsByAChoiceWithSumAndMeanAndIgnoresEmptyValues() {
    item("u", "a", "10", null);
    item("u", "a", "20", null);
    item("u", "b", null, null);
    item("u", null, "5", null);

    Map<String, Object> report =
        run(item, itemTable, "TRUE", "groupBy", "kind", "metrics", "count,sum(qty),avg(qty)");

    List<Map<String, Object>> groups = groups(report);
    assertThat(groups).extracting(g -> g.get("key")).containsExactly("a", "b", null);
    assertThat(groups.get(0))
        .containsEntry("count", 2L)
        .containsEntry("sum(qty)", new BigDecimal("30"))
        .containsEntry("avg(qty)", new BigDecimal("15.0000"));
    // A group with no value has no sum and no mean, but it still counts its row.
    assertThat(groups.get(1))
        .containsEntry("count", 1L)
        .containsEntry("sum(qty)", null)
        .containsEntry("avg(qty)", null);
    assertThat(groups.get(2))
        .containsEntry("count", 1L)
        .containsEntry("sum(qty)", new BigDecimal("5"));
    assertThat(totals(report))
        .containsEntry("count", 4L)
        .containsEntry("sum(qty)", new BigDecimal("35"))
        .containsEntry("avg(qty)", new BigDecimal("11.6667"));
    assertThat(report).containsEntry("truncated", false);
  }

  @Test
  void anEmptySetCountsZeroAndHasNoFigures() {
    Map<String, Object> report =
        run(
            item,
            itemTable,
            "TRUE",
            "groupBy",
            "kind",
            "metrics",
            "count,sum(qty),min(qty),max(qty)");

    assertThat(groups(report)).isEmpty();
    assertThat(totals(report))
        .containsEntry("count", 0L)
        .containsEntry("sum(qty)", null)
        .containsEntry("min(qty)", null)
        .containsEntry("max(qty)", null);
  }

  @Test
  void groupsADateByItsMonth() {
    item("u", "a", "1", "2026-01-15");
    item("u", "a", "1", "2026-01-31");
    item("u", "a", "1", "2026-03-01");
    item("u", "a", "1", null);

    List<Map<String, Object>> groups = groups(run(item, itemTable, "TRUE", "groupBy", "due:month"));

    assertThat(groups).extracting(g -> g.get("key")).containsExactly("2026-01", "2026-03", null);
    assertThat(groups).extracting(g -> g.get("count")).containsExactly(2L, 1L, 1L);
  }

  @Test
  void groupsByTheMonthOfCreation() {
    jdbc.getJdbcOperations()
        .execute(
            "INSERT INTO "
                + itemTable
                + " (created_by, created_at) VALUES ('u', '2026-02-10T23:30:00Z'),"
                + " ('u', '2026-02-28T01:00:00Z'), ('u', '2026-04-01T00:00:00Z')");

    List<Map<String, Object>> groups =
        groups(run(item, itemTable, "TRUE", "groupBy", "created_at:month"));

    assertThat(groups).extracting(g -> g.get("key")).containsExactly("2026-02", "2026-04");
  }

  @Test
  void groupsAProcessEntityByStatus() {
    jdbc.getJdbcOperations()
        .execute(
            "INSERT INTO "
                + ticketTable
                + " (created_by, kind, status) VALUES ('u', 'a', 'submitted'), ('u', 'a', 'submitted'),"
                + " ('u', 'b', 'approved')");

    List<Map<String, Object>> groups =
        groups(run(ticket, ticketTable, "TRUE", "groupBy", "status"));

    assertThat(groups).extracting(g -> g.get("key")).containsExactly("approved", "submitted");
    assertThat(groups).extracting(g -> g.get("count")).containsExactly(1L, 2L);
  }

  @Test
  void theGroupsAreCutAtTheLimitAndSaySo() {
    item("u", "a", "1", null);
    item("u", "b", "1", null);
    item("u", "c", "1", null);

    Map<String, Object> report = run(item, itemTable, "TRUE", "groupBy", "kind", "limit", "2");

    assertThat(groups(report)).hasSize(2);
    assertThat(report).containsEntry("truncated", true);
    assertThat(totals(report)).containsEntry("count", 3L);
  }

  @Test
  void onlyTheRowsGivenAreCounted() {
    item("mine", "a", "10", null);
    item("other", "a", "99", null);

    Map<String, Object> report =
        run(item, itemTable, "created_by = 'mine'", "groupBy", "kind", "metrics", "count,sum(qty)");

    assertThat(totals(report))
        .containsEntry("count", 1L)
        .containsEntry("sum(qty)", new BigDecimal("10"));
    assertThat(groups(report)).hasSize(1);
  }

  @Test
  void noGroupByGivesOnlyTheTotals() {
    item("u", "a", "4", null);

    Map<String, Object> report = run(item, itemTable, "TRUE", "metrics", "max(qty)");

    assertThat(report).containsEntry("groupBy", null);
    assertThat(groups(report)).isEmpty();
    assertThat(totals(report)).containsEntry("max(qty)", new BigDecimal("4"));
  }
}
