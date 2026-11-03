// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// Delivers the messages of the outbox after the change that wrote them has committed. A failed
// message waits longer before each new try; the one that failed does not hold back the others.
@Component
public class OutboxDispatcher {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
  private static final int BATCH = 50;
  private static final int ERROR_LENGTH = 500;

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;
  private final JobLock lock;
  private final ObjectMapper json;
  private final Map<String, OutboxHandler> handlers;

  public OutboxDispatcher(JdbcTemplate jdbc, PlatformTransactionManager transactions, JobLock lock,
      ObjectMapper json, List<OutboxHandler> handlers) {
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(transactions);
    this.lock = lock;
    this.json = json;
    this.handlers =
        handlers.stream().collect(Collectors.toMap(OutboxHandler::topic, Function.identity()));
  }

  @Scheduled(fixedDelayString = "${engine.outbox.poll-ms:2000}")
  public void run() {
    lock.runIfFree("engine-outbox", this::deliverDue);
  }

  void deliverDue() {
    List<Long> due = jdbc.queryForList("SELECT id FROM engine.outbox WHERE delivered_at IS NULL "
        + "AND next_attempt_at <= now() ORDER BY id LIMIT " + BATCH, Long.class);
    due.forEach(id -> transaction.executeWithoutResult(status -> deliver(id)));
  }

  private void deliver(long id) {
    List<Map<String, Object>> rows =
        jdbc.queryForList("SELECT topic, payload::text AS payload FROM engine.outbox WHERE id = ? "
            + "AND delivered_at IS NULL FOR UPDATE SKIP LOCKED", id);
    if (rows.isEmpty()) {
      return;
    }
    try {
      OutboxHandler handler = handlers.get((String) rows.get(0).get("topic"));
      if (handler == null) {
        throw new IllegalStateException("No handler for " + rows.get(0).get("topic"));
      }
      handler.deliver(json.readValue((String) rows.get(0).get("payload"),
          new TypeReference<Map<String, Object>>() {}));
      jdbc.update("UPDATE engine.outbox SET delivered_at = now(), attempts = attempts + 1, "
          + "last_error = NULL WHERE id = ?", id);
    } catch (RuntimeException e) {
      log.warn("Outbox message {} failed: {}", id, e.getMessage());
      String error = String.valueOf(e.getMessage());
      jdbc.update("UPDATE engine.outbox SET attempts = attempts + 1, last_error = ?, "
          + "next_attempt_at = now() + make_interval(secs => LEAST(300, power(2, attempts))) "
          + "WHERE id = ?", error.substring(0, Math.min(ERROR_LENGTH, error.length())), id);
    }
  }
}
