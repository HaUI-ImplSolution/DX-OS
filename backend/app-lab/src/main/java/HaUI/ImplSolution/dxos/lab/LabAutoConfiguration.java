// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Loads the Lab app when dxos.app=lab, after the core schema has been migrated.
@AutoConfiguration(
    beforeName = {"org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"})
@ConditionalOnProperty(name = "dxos.app", havingValue = "lab")
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION,
    classes = AutoConfiguration.class))
@EntityScan
@EnableJpaRepositories
public class LabAutoConfiguration {

  @Bean(initMethod = "migrate")
  @DependsOn("coreFlyway")
  public Flyway labFlyway(DataSource dataSource) {
    return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/lab")
        .table("flyway_schema_history_lab").baselineOnMigrate(true).baselineVersion("0").load();
  }
}
