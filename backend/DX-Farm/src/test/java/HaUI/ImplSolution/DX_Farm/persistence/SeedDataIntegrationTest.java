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
  void reviewedTreatmentRulesFitTheMigratedSchemaAndCanBeSeededAgain() throws Exception {
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
          ResultSet rows = statement.executeQuery(
              "SELECT count(*), max(length(notes)) FROM treatment_rules WHERE active = true")) {
        rows.next();
        assertThat(rows.getInt(1)).isEqualTo(3);
        assertThat(rows.getInt(2)).isGreaterThan(255);
      }
    }
  }
}
