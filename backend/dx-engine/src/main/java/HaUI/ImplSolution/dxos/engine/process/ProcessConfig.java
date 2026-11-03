// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import javax.sql.DataSource;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

// The embedded workflow engine. It uses the Spring transaction, so a change of a record and the
// step of its process commit or roll back together, and keeps its tables in the schema "engine".
@Configuration
public class ProcessConfig {

  @Bean
  @DependsOn("engineFlyway")
  public SpringProcessEngineConfiguration processEngineConfiguration(DataSource dataSource,
      PlatformTransactionManager transactionManager, ApplicationContext context) {
    SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
    configuration.setDataSource(dataSource);
    configuration.setTransactionManager(transactionManager);
    configuration.setApplicationContext(context);
    configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
    configuration.setDatabaseSchema("engine");
    configuration.setDatabaseTablePrefix("engine.");
    configuration.setTablePrefixIsSchema(true);
    configuration.setAsyncExecutorActivate(false);
    configuration.setDeploymentResources(
        new Resource[] {new ClassPathResource("processes/approval.bpmn20.xml"),
            new ClassPathResource("processes/work_item.bpmn20.xml"),
            new ClassPathResource("processes/reservation.bpmn20.xml")});
    return configuration;
  }

  @Bean(destroyMethod = "close")
  public ProcessEngine processEngine(SpringProcessEngineConfiguration configuration) {
    return configuration.buildProcessEngine();
  }

  @Bean
  public RuntimeService runtimeService(ProcessEngine engine) {
    return engine.getRuntimeService();
  }

  @Bean
  public TaskService taskService(ProcessEngine engine) {
    return engine.getTaskService();
  }

  @Bean
  public RepositoryService repositoryService(ProcessEngine engine) {
    return engine.getRepositoryService();
  }
}
