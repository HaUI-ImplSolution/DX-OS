// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import static org.assertj.core.api.Assertions.assertThat;

import HaUI.ImplSolution.dxos.engine.apply.DdlGenerator;
import HaUI.ImplSolution.dxos.engine.apply.Sql;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.ModelParser;
import java.time.OffsetDateTime;
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

// The quality metrics read the table the model creates, so they run on a real database.
@Testcontainers
class QualityMetricsTest {

  private static final String MODEL =
      """
      format: 0
      name: quality
      roles:
        - name: STAFF
      entities:
        item:
          fields:
            code: {type: text, required: true, pattern: "[A-Z]{3}"}
            kind: {type: choice, options: [software, hardware]}
            note: {type: text, required_when: "kind == software"}
            qty: {type: number, min: 1, max: 10}
            tags: {type: choice, multiple: true, options: [a, b]}
          access: {STAFF: all}
      """;

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  static NamedParameterJdbcTemplate jdbc;
  static Entity item;
  static String table;

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
    table = Sql.table(model, item);
  }

  @BeforeEach
  void empty() {
    jdbc.getJdbcOperations().execute("TRUNCATE " + table);
  }

  @Test
  void noRecordsStillGiveDefinedMeasures() {
    QualityMetric.Sample sample = sample("TRUE", 90);

    assertThat(sample.size()).isZero();
    Map<String, Object> code = field(new CompletenessMetric().measure(jdbc, sample), "code");
    assertThat(code)
        .containsEntry("filled", 0L)
        .containsEntry("missing", 0L)
        .containsEntry("rate", null);
    assertThat(field(new RequiredWhenMetric().measure(jdbc, sample), "note"))
        .containsEntry("applicable", 0L)
        .containsEntry("missing", 0L);
    assertThat(field(new ConstraintMetric().measure(jdbc, sample), "qty"))
        .containsEntry("violating", 0L);
    assertThat(new FreshnessMetric().measure(jdbc, sample))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .containsEntry("oldest", null)
        .containsEntry("newest", null)
        .containsEntry("stale", 0L);
  }

  @Test
  void completenessCountsEmptyTextsAndEmptyListsAsMissing() {
    insert("AAA", "software", "text", "5", "{a}", 0);
    insert("BBB", "hardware", "", null, "{}", 0);
    insert("CCC", "hardware", null, "2", null, 0);

    Object result = new CompletenessMetric().measure(jdbc, sample("TRUE", 90));

    assertThat(field(result, "code"))
        .containsEntry("filled", 3L)
        .containsEntry("rate", 1.0)
        .containsEntry("required", true);
    assertThat(field(result, "note"))
        .containsEntry("filled", 1L)
        .containsEntry("missing", 2L)
        .containsEntry("rate", 0.3333);
    assertThat(field(result, "qty")).containsEntry("filled", 2L);
    assertThat(field(result, "tags")).containsEntry("filled", 1L);
  }

  @Test
  void requiredWhenCountsTheRecordsThatMeetTheConditionAndHoldNoValue() {
    insert("AAA", "software", null, null, null, 0);
    insert("BBB", "software", "text", null, null, 0);
    insert("CCC", "hardware", null, null, null, 0);

    assertThat(field(new RequiredWhenMetric().measure(jdbc, sample("TRUE", 90)), "note"))
        .containsEntry("when", "kind == software")
        .containsEntry("applicable", 2L)
        .containsEntry("missing", 1L);
  }

  @Test
  void constraintsCountStoredValuesThatBreakTheRules() {
    // The table refuses such values, so the checks go first to stand in for data written around it.
    jdbc.getJdbcOperations()
        .execute(
            "DO $$ DECLARE c record; BEGIN FOR c IN SELECT conname FROM"
                + " pg_constraint WHERE conrelid = '"
                + table
                + "'::regclass AND contype = 'c' LOOP"
                + " EXECUTE 'ALTER TABLE "
                + table
                + " DROP CONSTRAINT ' || quote_ident(c.conname);"
                + " END LOOP; END $$");
    insert("AAA", "software", null, "5", "{a}", 0);
    insert("bad", "other", null, "50", "{a,z}", 0);

    Object result = new ConstraintMetric().measure(jdbc, sample("TRUE", 90));

    assertThat(field(result, "code")).containsEntry("violating", 1L);
    assertThat(field(result, "kind")).containsEntry("violating", 1L);
    assertThat(field(result, "qty")).containsEntry("violating", 1L);
    assertThat(field(result, "tags")).containsEntry("violating", 1L);
  }

  @Test
  void freshnessCountsRecordsNotChangedWithinTheDays() {
    insert("AAA", "software", null, null, null, 0);
    insert("BBB", "software", null, null, null, 200);

    Object result = new FreshnessMetric().measure(jdbc, sample("TRUE", 90));

    assertThat(result)
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .containsEntry("staleDays", 90)
        .containsEntry("stale", 1L)
        .doesNotContainEntry("oldest", null)
        .doesNotContainEntry("newest", null);
  }

  @Test
  void onlyTheRowsOfTheSampleAreMeasuredAndAMeasureRepeatsToTheSameAnswer() {
    insert("AAA", "software", "text", "5", "{a}", 0);
    insert("BBB", "hardware", null, null, null, 0);
    QualityMetric.Sample mine = sample("code = 'AAA'", 90);

    Object first = new CompletenessMetric().measure(jdbc, mine);

    assertThat(mine.size()).isEqualTo(1);
    assertThat(field(first, "note")).containsEntry("filled", 1L).containsEntry("rate", 1.0);
    assertThat(new CompletenessMetric().measure(jdbc, sample("code = 'AAA'", 90))).isEqualTo(first);
  }

  private static QualityMetric.Sample sample(String where, int staleDays) {
    String from = " FROM " + table + " WHERE " + where;
    Long size =
        jdbc.queryForObject("SELECT count(*)" + from, new MapSqlParameterSource(), Long.class);
    return new QualityMetric.Sample(
        item, from, new MapSqlParameterSource(), size, OffsetDateTime.now(), staleDays);
  }

  private static void insert(
      String code, String kind, String note, String qty, String tags, int daysOld) {
    jdbc.update(
        "INSERT INTO "
            + table
            + " (created_by, code, kind, note, qty, tags, updated_at)"
            + " VALUES ('tester', :code, :kind, :note, CAST(:qty AS numeric), CAST(:tags AS text[]),"
            + " now() - make_interval(days => :days))",
        new MapSqlParameterSource("code", code)
            .addValue("kind", kind)
            .addValue("note", note)
            .addValue("qty", qty)
            .addValue("tags", tags)
            .addValue("days", daysOld));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> field(Object result, String name) {
    return ((List<Map<String, Object>>) ((Map<String, Object>) result).get("fields"))
        .stream().filter(field -> name.equals(field.get("field"))).findFirst().orElseThrow();
  }
}
