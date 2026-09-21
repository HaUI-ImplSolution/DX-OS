// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Registers the shared services, entities and repositories, and migrates the core schema.
@AutoConfiguration(
    beforeName = {"org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"})
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION,
    classes = AutoConfiguration.class))
@EntityScan
@EnableJpaRepositories
public class CoreAutoConfiguration {

  @Bean(initMethod = "migrate")
  public Flyway coreFlyway(DataSource dataSource) {
    return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/core")
        .table("flyway_schema_history_core").load();
  }
}
