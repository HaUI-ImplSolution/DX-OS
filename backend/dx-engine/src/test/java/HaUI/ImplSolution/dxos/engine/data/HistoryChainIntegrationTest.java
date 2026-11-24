// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// The hash of each history line is computed by a function of the database, so the chain is checked
// on a real PostgreSQL with the migrations of the engine.
@Testcontainers
class HistoryChainIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static JdbcTemplate jdbc;
  private static HistoryChain chain;

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas("engine")
        .locations("classpath:db/migration/engine")
        .table("flyway_schema_history_engine")
        .load()
        .migrate();
    String url =
        POSTGRES.getJdbcUrl()
            + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
            + "currentSchema=engine";
    DriverManagerDataSource source =
        new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(source);
    chain = new HistoryChain(jdbc, new DataSourceTransactionManager(source));
  }

  @BeforeEach
  void emptyHistory() {
    jdbc.execute("TRUNCATE engine.record_event RESTART IDENTITY");
  }

  private static long write(String action) {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO engine.record_event (model, entity, record_id, action, actor, to_status)"
                + " VALUES ('shop', 'item', 1, ?, 'user-1', 'open') RETURNING id",
            Long.class,
            action);
    return id;
  }

  private static long writeLinked(String action) {
    long id = write(action);
    chain.link(id);
    return id;
  }

  @Test
  void anEmptyHistoryHasNoHeadAndNoBreak() {
    assertNull(chain.head());
    assertNull(chain.firstBreak());
    assertNull(chain.hashOf(1));
  }

  @Test
  void eachLineIsLinkedToTheOneBeforeIt() {
    long first = writeLinked("created");
    long second = writeLinked("submitted");

    assertNotNull(chain.hashOf(first));
    assertNotEquals(chain.hashOf(first), chain.hashOf(second));
    assertEquals(
        chain.hashOf(first),
        jdbc.queryForObject(
            "SELECT prev_hash FROM engine.record_event WHERE id = ?", String.class, second));
    assertNull(
        jdbc.queryForObject(
            "SELECT prev_hash FROM engine.record_event WHERE id = ?", String.class, first));
    assertNull(chain.firstBreak());
  }

  @Test
  void theHeadIsTheLastLineAndItsHash() {
    writeLinked("created");
    long last = writeLinked("submitted");

    Map<String, Object> head = chain.head();

    assertEquals(last, ((Number) head.get("id")).longValue());
    assertEquals(chain.hashOf(last), head.get("hash"));
  }

  @Test
  void aLineWhoseContentWasChangedIsTheFirstBreak() {
    writeLinked("created");
    long second = writeLinked("submitted");
    writeLinked("approved");

    jdbc.update("UPDATE engine.record_event SET action = 'rejected' WHERE id = ?", second);

    Map<String, Object> broken = chain.firstBreak();
    assertEquals(second, ((Number) broken.get("id")).longValue());
    assertEquals("its content was changed", broken.get("reason"));
  }

  @Test
  void aChangedStatusIsDetectedToo() {
    long first = writeLinked("created");

    jdbc.update("UPDATE engine.record_event SET to_status = 'approved' WHERE id = ?", first);

    assertEquals("its content was changed", chain.firstBreak().get("reason"));
  }

  @Test
  void aRemovedLineBreaksTheLineAfterIt() {
    writeLinked("created");
    long second = writeLinked("submitted");
    long third = writeLinked("approved");

    jdbc.update("DELETE FROM engine.record_event WHERE id = ?", second);

    Map<String, Object> broken = chain.firstBreak();
    assertEquals(third, ((Number) broken.get("id")).longValue());
    assertEquals("the line before it was changed or removed", broken.get("reason"));
  }

  @Test
  void aLineSlippedInAfterwardsIsNotLinked() {
    writeLinked("created");
    long slipped = write("forged");

    Map<String, Object> broken = chain.firstBreak();

    assertEquals(slipped, ((Number) broken.get("id")).longValue());
    assertEquals("not linked", broken.get("reason"));
  }

  @Test
  void aNoteIsNotPartOfTheChainSoItCanBeCorrected() {
    long first = writeLinked("created");

    jdbc.update(
        "UPDATE engine.record_event SET note = 'corrected', actor_name = 'Ann' WHERE id = ?",
        first);

    assertNull(chain.firstBreak());
  }

  @Test
  void linesWrittenBeforeTheChainExistedAreLinkedAtStartup() {
    write("created");
    write("submitted");
    write("approved");
    assertEquals("not linked", chain.firstBreak().get("reason"));

    chain.linkUnlinked();

    assertNull(chain.firstBreak());
    assertTrue(
        jdbc.queryForObject(
                "SELECT count(*) FROM engine.record_event WHERE hash IS NOT NULL", Long.class)
            == 3);
  }

  @Test
  void theHashDoesNotDependOnTheTimeZoneOfTheSession() {
    long first = writeLinked("created");
    String before = chain.hashOf(first);

    jdbc.execute("SET TIME ZONE 'Asia/Tokyo'");
    try {
      assertNull(chain.firstBreak());
      assertEquals(before, chain.hashOf(first));
    } finally {
      jdbc.execute("RESET TIME ZONE");
    }
  }
}
