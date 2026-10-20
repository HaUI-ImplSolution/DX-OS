// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host.persistence;

import HaUI.ImplSolution.dxos.farm.service.FarmKnowledgeNamespace;
import java.io.IOException;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import tools.jackson.databind.json.JsonMapper;

// Entities and repositories of the core and the apps, which the JPA slice would not find from the
// host package alone.
@TestConfiguration
@EntityScan(basePackages = "HaUI.ImplSolution.dxos")
@EnableJpaRepositories(basePackages = "HaUI.ImplSolution.dxos")
class PersistenceTestConfig {

  // The JPA slice has no Jackson auto-configuration, which the source catalogue needs.
  @Bean
  FarmKnowledgeNamespace farmKnowledgeNamespace() throws IOException {
    return new FarmKnowledgeNamespace(JsonMapper.builder().build());
  }
}
