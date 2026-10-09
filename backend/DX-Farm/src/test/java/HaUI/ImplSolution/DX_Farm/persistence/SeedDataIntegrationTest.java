package HaUI.ImplSolution.DX_Farm.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SeedDataIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @Test
  void reviewedTreatmentRulesFitTheCurrentSchemaAndCanBeSeededAgain() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration").cleanDisabled(false).load().clean();
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration").load().migrate();

    try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(), POSTGRES.getPassword())) {
      ClassPathResource seed = new ClassPathResource("data.sql");
      ScriptUtils.executeSqlScript(connection, seed);
      ScriptUtils.executeSqlScript(connection, seed);

      try (Statement statement = connection.createStatement();
          ResultSet rows =
              statement.executeQuery("SELECT count(*), count(DISTINCT (disease_id, pesticide_id))"
                  + " FROM treatment_rules WHERE active = true")) {
        rows.next();
        assertThat(rows.getInt(1)).isEqualTo(2);
        assertThat(rows.getInt(2)).isEqualTo(2);
      }

      try (Statement statement = connection.createStatement();
          ResultSet column =
              statement.executeQuery("SELECT data_type FROM information_schema.columns"
                  + " WHERE table_name = 'treatment_rules' AND column_name = 'notes'")) {
        column.next();
        assertThat(column.getString(1)).isEqualTo("text");
      }
    }
  }
}
