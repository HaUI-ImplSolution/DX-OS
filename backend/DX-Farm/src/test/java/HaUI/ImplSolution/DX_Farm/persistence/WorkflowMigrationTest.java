// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Replays the upgrade of a database that already holds diagnoses decided before workflow cases
// existed.
@Testcontainers
class WorkflowMigrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private Connection connect() throws SQLException {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
        POSTGRES.getPassword());
  }

  private static Flyway flyway(String target) {
    return Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration").cleanDisabled(false).target(target).load();
  }

  private static List<String> column(Statement statement, String sql) throws SQLException {
    List<String> values = new ArrayList<>();
    try (ResultSet rows = statement.executeQuery(sql)) {
      while (rows.next()) {
        values.add(rows.getString(1));
      }
    }
    return values;
  }

  @Test
  void existingDiagnosesGetACaseWithTheirDecisionHistory() throws Exception {
    flyway("latest").clean();
    flyway("3").migrate();
    try (Connection connection = connect(); Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO households (code, full_name, created_at, updated_at)"
          + " VALUES ('H1', 'A', now(), now())");
      statement.executeUpdate("INSERT INTO parcels (code, farmer_name, area_hectares, created_at,"
          + " updated_at, household_id) SELECT 'P1', 'A', 1, now(), now(), id FROM households");
      statement.executeUpdate("INSERT INTO crop_seasons (parcel_id, rice_variety, start_date,"
          + " status) SELECT id, 'OM', date '2026-09-01', 'ACTIVE' FROM parcels");
      statement.executeUpdate("INSERT INTO diseases (code, name) VALUES ('D1', 'Blast')");
      for (String[] diagnosis : new String[][] {{"PENDING", "NULL", "NULL", "NULL"},
          {"APPROVED", "'Eng One'", "'eng-1'", "NULL"},
          {"REJECTED", "'Eng Two'", "'eng-2'", "'Blurry'"}}) {
        statement.executeUpdate("INSERT INTO disease_reports (crop_season_id, image_ref,"
            + " reported_at, reported_by) SELECT id, 'x.jpg', now(), 'A' FROM crop_seasons");
        statement.executeUpdate("INSERT INTO diagnoses (approval_status, confidence, diagnosed_at,"
            + " model_version, recommendation_text, disease_id, disease_report_id, decided_by,"
            + " decided_by_user_id, rejection_reason, decided_at) SELECT '%s', 0.9, now(), 'm',"
                .formatted(diagnosis[0])
            + " 't', (SELECT id FROM diseases), (SELECT max(id) FROM disease_reports), %s, %s, %s,"
                .formatted(diagnosis[1], diagnosis[2], diagnosis[3])
            + (diagnosis[0].equals("PENDING") ? " NULL" : " now()"));
      }
    }

    flyway("4").migrate();

    try (Connection connection = connect(); Statement statement = connection.createStatement()) {
      assertThat(column(statement,
          "SELECT c.status || ':' || count(e.id) FROM workflow_case c JOIN workflow_event e"
              + " ON e.workflow_case_id = c.id GROUP BY c.status ORDER BY c.status"))
          .containsExactly("APPROVED:2", "PENDING:1", "REJECTED:2");
      assertThat(column(statement,
          "SELECT e.action || ':' || e.actor_user_id || ':' || coalesce(e.reason, '-')"
              + " FROM workflow_event e WHERE e.actor_user_id IS NOT NULL ORDER BY e.actor_user_id"))
          .containsExactly("APPROVED:eng-1:-", "REJECTED:eng-2:Blurry");
      assertThat(column(statement, "SELECT count(*) FROM workflow_case WHERE status = 'PENDING'"
          + " AND case_type = 'DIAGNOSIS_APPROVAL'")).containsExactly("1");
    }

    flyway("6").migrate();
    flyway("latest").migrate();

    try (Connection connection = connect(); Statement statement = connection.createStatement()) {
      assertThat(column(statement,
          "SELECT c.status || ':' || count(e.id) FROM workflow_case c JOIN workflow_event e"
              + " ON e.workflow_case_id = c.id GROUP BY c.status ORDER BY c.status"))
          .containsExactly("APPROVED:2", "PENDING:1", "REJECTED:2");
      assertThat(column(statement,
          "SELECT status || ':' || count(*) FROM disease_reports GROUP BY status"))
          .containsExactly("PROCESSED:3");
    }
  }
}
