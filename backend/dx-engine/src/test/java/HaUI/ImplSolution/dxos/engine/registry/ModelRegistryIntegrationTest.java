// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// The registry holds the latest stored version of each model; the stored versions and the runs
// that apply them are rows of the database.
@Testcontainers
class ModelRegistryIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static JdbcTemplate jdbc;
  private ModelRegistry registry;

  private static String model(String name, String extraField) {
    return "format: 1\nname: "
        + name
        + "\nroles:\n  - name: A\nentities:\n  item:\n    fields:\n"
        + "      a: {type: text}\n"
        + extraField
        + "    access: {A: all}\n";
  }

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas("engine")
        .locations("classpath:db/migration/engine")
        .table("flyway_schema_history_engine")
        .load()
        .migrate();
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
  }

  @BeforeEach
  void emptyRegistry() {
    jdbc.execute("TRUNCATE engine.apply_run, engine.model_version RESTART IDENTITY CASCADE");
    registry = new ModelRegistry(jdbc);
  }

  private static void store(String name, int version, String content) {
    jdbc.update(
        "INSERT INTO engine.model_version (model, version, hash, content, applied_by)"
            + " VALUES (?, ?, 'h', ?, 'tester')",
        name,
        version,
        content);
  }

  private static void run(String name, String status) {
    jdbc.update(
        "INSERT INTO engine.apply_run (model, hash, status, actor) VALUES (?, 'h', ?, 'tester')",
        name,
        status);
  }

  @Test
  void aRegistryWithNothingStoredKnowsNoModel() {
    registry.reload();

    assertTrue(registry.find("shop").isEmpty());
    assertTrue(registry.all().isEmpty());
    assertThrows(ResourceNotFoundException.class, () -> registry.require("shop"));
  }

  @Test
  void theLatestStoredVersionOfEachModelRuns() {
    store("shop", 1, model("shop", ""));
    store("shop", 2, model("shop", "      b: {type: number}\n"));
    store("loan", 1, model("loan", ""));

    registry.reload();

    assertEquals(2, registry.all().size());
    assertEquals(
        List.of("a", "b"),
        List.copyOf(registry.require("shop").entities().get("item").fields().keySet()));
    assertEquals(2, registry.version("shop"));
    assertEquals(1, registry.version("loan"));
  }

  @Test
  void aCallerThatReadAnOldVersionOfTheModelIsToldToReadItAgain() {
    store("shop", 1, model("shop", ""));
    store("shop", 2, model("shop", ""));
    registry.reload();

    registry.checkVersion("shop", 2L);
    registry.checkVersion("shop", null);
    InvalidStateException stale =
        assertThrows(InvalidStateException.class, () -> registry.checkVersion("shop", 1L));
    assertEquals("MODEL_VERSION_CHANGED", stale.getReason());
  }

  @Test
  void aModelThatIsNotAppliedHasNoVersion() {
    registry.reload();

    assertThrows(ResourceNotFoundException.class, () -> registry.version("shop"));
  }

  @Test
  void aStoredModelThatFailsTheChecksIsLeftOutWithItsReasons() {
    store("good", 1, model("good", ""));
    store("bad", 1, "format: 1\nname: bad\n");

    registry.reload();

    assertTrue(registry.find("good").isPresent());
    assertTrue(registry.find("bad").isEmpty());
    assertTrue(registry.leftOut().containsKey("bad"));
    assertTrue(
        registry.leftOut().get("bad").stream().anyMatch(r -> r.contains("roles is required")));
    InvalidStateException left =
        assertThrows(InvalidStateException.class, () -> registry.require("bad"));
    assertTrue(
        left.getMessage().contains("is left out because its stored version fails the checks"));
    assertTrue(registry.warnings("good").isEmpty());
  }

  @Test
  void aModelWhoseLastRunIsRunningOrFailedIsHeldForReconciliation() {
    store("busy", 1, model("busy", ""));
    store("broken", 1, model("broken", ""));
    store("fine", 1, model("fine", ""));
    run("busy", "running");
    run("broken", "failed");
    run("fine", "failed");
    run("fine", "done");

    registry.reload();

    assertTrue(registry.isHeld("busy"));
    assertTrue(registry.isHeld("broken"));
    assertFalse(registry.isHeld("fine"), "only the latest run of a model counts");
    assertThrows(InvalidStateException.class, () -> registry.require("busy"));
    assertEquals("fine", registry.require("fine").name());
    assertEquals(
        List.of("broken", "busy"), registry.held().stream().map(m -> m.name()).sorted().toList());
  }

  @Test
  void anApplyHoldsTheModelUntilItEnds() {
    store("shop", 1, model("shop", ""));
    registry.reload();

    registry.beginApply("shop");
    assertTrue(registry.isHeld("shop"));
    assertThrows(InvalidStateException.class, () -> registry.require("shop"));

    registry.endApply("shop");
    assertFalse(registry.isHeld("shop"));
    assertEquals("shop", registry.require("shop").name());
  }

  @Test
  void aReloadAfterANewVersionPicksItUp() {
    store("shop", 1, model("shop", ""));
    registry.reload();
    assertEquals(1, registry.version("shop"));

    store("shop", 2, model("shop", "      b: {type: number}\n"));
    registry.reload();

    assertEquals(2, registry.version("shop"));
    assertTrue(registry.require("shop").entities().get("item").field("b").isPresent());
  }
}
