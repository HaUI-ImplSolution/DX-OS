// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.persistence;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Entities and repositories of all modules, which the JPA slice would not find from the engine
// package alone.
@TestConfiguration
@EntityScan(basePackages = "HaUI.ImplSolution.dxos")
@EnableJpaRepositories(basePackages = "HaUI.ImplSolution.dxos")
class PersistenceTestConfig {}
