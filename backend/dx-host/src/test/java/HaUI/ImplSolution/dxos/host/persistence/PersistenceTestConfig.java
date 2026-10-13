// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host.persistence;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Entities and repositories of the core and the apps, which the JPA slice would not find from the
// host package alone.
@TestConfiguration
@EntityScan(basePackages = "HaUI.ImplSolution.dxos")
@EnableJpaRepositories(basePackages = "HaUI.ImplSolution.dxos")
class PersistenceTestConfig {
}
