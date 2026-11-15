// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Migrates the schema "engine", which holds the model versions, the apply history and the
// workflow engine tables.
@Configuration
public class EngineConfig {

  @Bean(initMethod = "migrate")
  public Flyway engineFlyway(DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .schemas("engine")
        .defaultSchema("engine")
        .createSchemas(true)
        .locations("classpath:db/migration/engine")
        .table("flyway_schema_history_engine")
        .load();
  }
}
