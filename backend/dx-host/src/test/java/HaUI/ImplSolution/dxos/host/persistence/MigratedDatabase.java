// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host.persistence;

import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Applies the real Flyway migrations to a Testcontainers database before the Spring context
// starts, so the @DataJpaTest slice validates its entities against the migrated schema.
final class MigratedDatabase {

  private MigratedDatabase() {}

  static void migrate(PostgreSQLContainer postgres) {
    migrate(postgres, "classpath:db/migration/core", "flyway_schema_history_core");
    migrate(postgres, "classpath:db/migration/farm", "flyway_schema_history_farm");
  }

  private static void migrate(PostgreSQLContainer postgres, String location, String table) {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations(location).table(table).baselineOnMigrate(true).baselineVersion("0").load()
        .migrate();
  }
}
