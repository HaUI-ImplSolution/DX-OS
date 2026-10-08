// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

// Replays the upgrade of a database that already holds parcels written before households existed.
@Testcontainers
class HouseholdMigrationTest {

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
  void legacyParcelsAreGroupedIntoHouseholdsByOwnerName() throws Exception {
    flyway("latest").clean();
    flyway("1").migrate();
    try (Connection connection = connect(); Statement statement = connection.createStatement()) {
      for (String[] parcel : new String[][] {{"A-1", "Nguyễn Văn Nông"},
          {"A-2", "  nguyễn văn nông "}, {"B-1", "Trần Văn Bình"}}) {
        statement.executeUpdate("INSERT INTO parcels (code, farmer_name, area_hectares,"
            + " created_at, updated_at) VALUES ('%s', '%s', 1, now(), now())".formatted(parcel[0],
                parcel[1]));
      }
    }

    flyway("latest").migrate();

    try (Connection connection = connect(); Statement statement = connection.createStatement()) {
      assertThat(column(statement, "SELECT code || ':' || full_name FROM households ORDER BY code"))
          .containsExactly("H0001:Nguyễn Văn Nông", "H0002:Trần Văn Bình");
      assertThat(column(statement,
          "SELECT p.code || '>' || h.code FROM parcels p JOIN households h ON h.id = p.household_id"
              + " ORDER BY p.code"))
          .containsExactly("A-1>H0001", "A-2>H0001", "B-1>H0002");
      assertThat(column(statement,
          "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'parcels'"
              + " AND column_name = 'household_id'"))
          .containsExactly("NO");
      assertThat(column(statement,
          "SELECT count(*) FROM households WHERE keycloak_user_id" + " IS NOT NULL"))
          .containsExactly("0");
    }
  }

  @Test
  void databaseCreatedBeforeFlywayIsBaselinedAndUpgraded() throws Exception {
    flyway("latest").clean();
    flyway("1").migrate();
    try (Connection connection = connect(); Statement statement = connection.createStatement()) {
      // Same state as a database built by Hibernate: schema present, no Flyway history.
      statement.executeUpdate("DROP TABLE flyway_schema_history");
      statement.executeUpdate("INSERT INTO parcels (code, farmer_name, area_hectares, created_at,"
          + " updated_at) VALUES ('OLD-1', 'Lê Văn C', 1, now(), now())");
    }

    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("1").load()
        .migrate();

    try (Connection connection = connect(); Statement statement = connection.createStatement()) {
      assertThat(
          column(statement,
              "SELECT h.code || ':' || h.full_name FROM parcels p"
                  + " JOIN households h ON h.id = p.household_id"))
          .containsExactly("H0001:Lê Văn C");
      assertThat(
          column(statement, "SELECT version FROM flyway_schema_history ORDER BY installed_rank"))
          .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
    }
  }

  @Test
  void parcelCodeStaysUniqueAmongNotDeletedParcelsOnly() throws Exception {
    flyway("latest").clean();
    flyway("latest").migrate();
    try (Connection connection = connect(); Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO households (code, full_name, created_at, updated_at)"
          + " VALUES ('HX', 'X', now(), now())");
      String insert = "INSERT INTO parcels (code, farmer_name, area_hectares, created_at,"
          + " updated_at, household_id, approval_status, deleted_at) SELECT 'DUP', 'X', 1, now(),"
          + " now(), id, 'APPROVED', %s FROM households WHERE code = 'HX'";
      statement.executeUpdate(insert.formatted("now()"));
      statement.executeUpdate(insert.formatted("NULL"));
      assertThatThrownBy(() -> statement.executeUpdate(insert.formatted("NULL")))
          .isInstanceOf(SQLException.class);
    }
  }
}
