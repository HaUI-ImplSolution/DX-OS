package HaUI.ImplSolution.DX_Farm.persistence;

import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Applies the real Flyway migrations to a Testcontainers database before the Spring context
// starts, so the @DataJpaTest slice validates its entities against the migrated schema.
final class MigratedDatabase {

  private MigratedDatabase() {}

  static void migrate(PostgreSQLContainer postgres) {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration").load().migrate();
  }
}
