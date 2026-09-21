// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm;

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

// Loads the Farm app when dxos.app=farm, after the core schema has been migrated.
@AutoConfiguration(
    beforeName = {"org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"})
@ConditionalOnProperty(name = "dxos.app", havingValue = "farm")
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION,
    classes = AutoConfiguration.class))
@EntityScan
@EnableJpaRepositories
public class FarmAutoConfiguration {

  @Bean(initMethod = "migrate")
  @DependsOn("coreFlyway")
  public Flyway farmFlyway(DataSource dataSource) {
    return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/farm")
        .table("flyway_schema_history_farm").baselineOnMigrate(true).baselineVersion("0").load();
  }
}
