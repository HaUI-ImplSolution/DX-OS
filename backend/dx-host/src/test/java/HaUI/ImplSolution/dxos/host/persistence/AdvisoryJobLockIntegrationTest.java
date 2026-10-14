// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import HaUI.ImplSolution.dxos.core.scheduling.AdvisoryJobLock;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// A scheduled job runs on one instance at a time; a second connection stands in for a second
// backend instance.
@DataJpaTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({AdvisoryJobLock.class, PersistenceTestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdvisoryJobLockIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    MigratedDatabase.migrate(POSTGRES);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired
  AdvisoryJobLock lock;

  @Test
  void jobRunsWhenNobodyHoldsTheLock() {
    AtomicBoolean ran = new AtomicBoolean();

    assertThat(lock.runIfFree("test-free", () -> ran.set(true))).isTrue();
    assertThat(ran).isTrue();
  }

  @Test
  void secondInstanceSkipsTheJobWhileTheFirstRuns() {
    AtomicBoolean inner = new AtomicBoolean(true);
    AtomicBoolean other = new AtomicBoolean();

    boolean ran = lock.runIfFree("test-busy", () -> {
      inner.set(lock.runIfFree("test-busy", () -> {
      }));
      other.set(lock.runIfFree("test-other", () -> {
      }));
    });

    assertThat(ran).isTrue();
    assertThat(inner).isFalse();
    assertThat(other).isTrue();
  }

  @Test
  void lockIsReleasedAfterTheJobEvenWhenItFails() {
    assertThatThrownBy(() -> lock.runIfFree("test-fail", () -> {
      throw new IllegalStateException("boom");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(lock.runIfFree("test-fail", () -> {
    })).isTrue();
  }
}
