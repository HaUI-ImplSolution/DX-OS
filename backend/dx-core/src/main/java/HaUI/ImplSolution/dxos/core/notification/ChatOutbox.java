// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// Chat messages that could not be delivered, kept in the database and sent again by
// ChatOutboxJob. The wait doubles after each failure (1, 2, 4 ... up to 60 minutes) and the
// message is given up after MAX_ATTEMPTS.
@Component
public class ChatOutbox {

  public static final int MAX_ATTEMPTS = 8;
  private static final Duration MAX_DELAY = Duration.ofMinutes(60);
  private static final int MAX_ERROR_LENGTH = 200;
  private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {};

  public record Entry(long id, String channelKey, Map<String, Object> payload, int attempts) {}

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public ChatOutbox(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  // Called after the first delivery attempt failed.
  public void enqueue(String channelKey, Map<String, Object> payload, String error) {
    jdbcTemplate.update(
        "INSERT INTO chat_outbox (channel_key, payload, last_error, next_attempt_at)"
            + " VALUES (?, ?::jsonb, ?, ?)",
        channelKey, objectMapper.writeValueAsString(payload), shorten(error),
        java.sql.Timestamp.from(Instant.now(clock).plus(delay(1))));
  }

  public List<Entry> due(int limit) {
    return jdbcTemplate.query(
        "SELECT id, channel_key, payload::text, attempts FROM chat_outbox"
            + " WHERE status = 'PENDING' AND next_attempt_at <= ? ORDER BY id LIMIT ?",
        (rs, row) -> new Entry(rs.getLong(1), rs.getString(2),
            objectMapper.readValue(rs.getString(3), PAYLOAD), rs.getInt(4)),
        java.sql.Timestamp.from(Instant.now(clock)), limit);
  }

  public void delete(long id) {
    jdbcTemplate.update("DELETE FROM chat_outbox WHERE id = ?", id);
  }

  public void failedAgain(Entry entry, String error) {
    int attempts = entry.attempts() + 1;
    if (attempts > MAX_ATTEMPTS) {
      jdbcTemplate.update(
          "UPDATE chat_outbox SET status = 'FAILED', attempts = ?, last_error = ? WHERE id = ?",
          attempts, shorten(error), entry.id());
      return;
    }
    jdbcTemplate.update(
        "UPDATE chat_outbox SET attempts = ?, last_error = ?, next_attempt_at = ? WHERE id = ?",
        attempts, shorten(error), java.sql.Timestamp.from(Instant.now(clock).plus(delay(attempts))),
        entry.id());
  }

  public int purgeFailed(Duration keep) {
    return jdbcTemplate.update(
        "DELETE FROM chat_outbox WHERE status = 'FAILED' AND next_attempt_at < ?",
        java.sql.Timestamp.from(Instant.now(clock).minus(keep)));
  }

  public static Duration delay(int attempts) {
    Duration delay = Duration.ofMinutes(1L << Math.min(attempts - 1, 6));
    return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
  }

  private static String shorten(String error) {
    return error == null || error.length() <= MAX_ERROR_LENGTH ? error
        : error.substring(0, MAX_ERROR_LENGTH);
  }
}
