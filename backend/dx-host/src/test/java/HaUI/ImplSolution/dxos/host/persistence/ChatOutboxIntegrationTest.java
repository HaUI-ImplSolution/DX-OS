// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import HaUI.ImplSolution.dxos.core.notification.ChatOutbox;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

// The retry queue of undelivered chat messages, on the migrated schema: when a message is due,
// how the wait grows, and when it is given up.
@DataJpaTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ChatOutboxIntegrationTest {

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
  JdbcTemplate jdbc;

  private final Instant start = Instant.parse("2026-10-28T08:00:00Z");
  private Instant now;
  private ChatOutbox outbox;

  @BeforeEach
  void setUp() {
    jdbc.update("DELETE FROM chat_outbox");
    now = start;
    Clock clock = new Clock() {
      @Override
      public java.time.ZoneId getZone() {
        return ZoneOffset.UTC;
      }

      @Override
      public Clock withZone(java.time.ZoneId zone) {
        return this;
      }

      @Override
      public Instant instant() {
        return now;
      }
    };
    outbox = new ChatOutbox(jdbc, JsonMapper.builder().build(), clock);
  }

  @Test
  void messageIsDueAfterTheFirstWaitAndKeepsItsPayload() {
    outbox.enqueue("engineer", Map.of("text", "xin chào", "n", 2), "HTTP 503");

    assertThat(outbox.due(10)).isEmpty();

    now = start.plus(Duration.ofMinutes(1));
    var due = outbox.due(10);
    assertThat(due).hasSize(1);
    assertThat(due.get(0).channelKey()).isEqualTo("engineer");
    assertThat(due.get(0).payload()).containsEntry("text", "xin chào").containsEntry("n", 2);
  }

  @Test
  void waitDoublesAfterEachFailureAndIsCappedAtAnHour() {
    assertThat(ChatOutbox.delay(1)).isEqualTo(Duration.ofMinutes(1));
    assertThat(ChatOutbox.delay(2)).isEqualTo(Duration.ofMinutes(2));
    assertThat(ChatOutbox.delay(4)).isEqualTo(Duration.ofMinutes(8));
    assertThat(ChatOutbox.delay(7)).isEqualTo(Duration.ofMinutes(60));
    assertThat(ChatOutbox.delay(20)).isEqualTo(Duration.ofMinutes(60));
  }

  @Test
  void failedMessageWaitsLongerAndIsGivenUpAfterTheLastAttempt() {
    outbox.enqueue("admin", Map.of("text", "a"), "HTTP 503");
    now = start.plus(Duration.ofMinutes(1));
    var entry = outbox.due(10).get(0);

    outbox.failedAgain(entry, "HTTP 503");
    assertThat(outbox.due(10)).isEmpty();
    now = now.plus(Duration.ofMinutes(2));
    entry = outbox.due(10).get(0);
    assertThat(entry.attempts()).isEqualTo(2);

    for (int attempt = 2; attempt <= ChatOutbox.MAX_ATTEMPTS; attempt++) {
      outbox.failedAgain(entry, "HTTP 503");
      now = now.plus(Duration.ofHours(2));
      if (attempt < ChatOutbox.MAX_ATTEMPTS) {
        entry = outbox.due(10).get(0);
      }
    }

    assertThat(outbox.due(10)).isEmpty();
    assertThat(jdbc.queryForObject("SELECT status FROM chat_outbox", String.class))
        .isEqualTo("FAILED");
    assertThat(outbox.purgeFailed(Duration.ofDays(7))).isZero();
    now = now.plus(Duration.ofDays(8));
    assertThat(outbox.purgeFailed(Duration.ofDays(7))).isEqualTo(1);
  }

  @Test
  void deliveredMessageIsRemoved() {
    outbox.enqueue("admin", Map.of("text", "a"), "timeout");
    now = start.plus(Duration.ofMinutes(1));

    outbox.delete(outbox.due(10).get(0).id());

    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_outbox", Integer.class)).isZero();
  }
}
