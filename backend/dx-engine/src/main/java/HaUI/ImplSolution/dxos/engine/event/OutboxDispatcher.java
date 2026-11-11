// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// Delivers the messages of the outbox after the change that wrote them has committed. A failed
// message waits longer before each new try, and stops after the last allowed try until someone
// retries it; the one that failed does not hold back the messages of other records. The lines of
// one record are delivered in order: a line waits while an older line of the same record is not
// delivered, also when that one has stopped and waits for a retry. When the target of a topic
// cannot be reached, the other messages of that topic wait for the next round, so a target that
// does not answer costs one wait per round and does not hold back the other topics.
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
  private final int maxAttempts;

  public OutboxDispatcher(
      JdbcTemplate jdbc,
      PlatformTransactionManager transactions,
      JobLock lock,
      ObjectMapper json,
      List<OutboxHandler> handlers,
      @Value("${engine.outbox.max-attempts:20}") int maxAttempts) {
    this.jdbc = jdbc;
    this.maxAttempts = Math.max(1, maxAttempts);
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
    Set<String> unreachable = new HashSet<>();
    boolean again = true;
    while (again) {
      List<Map<String, Object>> messages = due(unreachable);
      again = !messages.isEmpty();
      for (Map<String, Object> message : messages) {
        long id = ((Number) message.get("id")).longValue();
        boolean[] reachable = {true};
        transaction.executeWithoutResult(status -> reachable[0] = deliver(id));
        if (!reachable[0]) {
          unreachable.add((String) message.get("topic"));
          break;
        }
      }
    }
  }

  private List<Map<String, Object>> due(Set<String> failedTopics) {
    String skip =
        failedTopics.isEmpty()
            ? ""
            : " AND o.topic NOT IN ("
                + String.join(", ", failedTopics.stream().map(t -> "?").toList())
                + ")";
    return jdbc.queryForList(
        "SELECT o.id, o.topic FROM engine.outbox o WHERE o.delivered_at IS NULL "
            + "AND o.next_attempt_at <= now()"
            + skip
            + " AND NOT EXISTS (SELECT 1 FROM "
            + "engine.outbox p WHERE o.topic = 'record.event' AND p.topic = o.topic "
            + "AND p.delivered_at IS NULL AND p.id < o.id AND p.model = o.model "
            + "AND p.entity = o.entity AND p.record_id = o.record_id) ORDER BY o.id LIMIT "
            + BATCH,
        new ArrayList<>(failedTopics).toArray());
  }

  // False when the target of the message could not be reached; true otherwise, also when the try
  // failed for another reason.
  private boolean deliver(long id) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT topic, payload::text AS payload FROM engine.outbox WHERE id = ? "
                + "AND delivered_at IS NULL FOR UPDATE SKIP LOCKED",
            id);
    if (rows.isEmpty()) {
      return true;
    }
    try {
      OutboxHandler handler = handlers.get((String) rows.get(0).get("topic"));
      if (handler == null) {
        throw new IllegalStateException("No handler for " + rows.get(0).get("topic"));
      }
      handler.deliver(
          json.readValue(
              (String) rows.get(0).get("payload"), new TypeReference<Map<String, Object>>() {}));
      jdbc.update(
          "UPDATE engine.outbox SET delivered_at = now(), attempts = attempts + 1, "
              + "last_error = NULL WHERE id = ?",
          id);
      return true;
    } catch (RuntimeException e) {
      log.warn("Outbox message {} failed: {}", id, e.getMessage());
      boolean reachable = !(e instanceof TargetUnreachableException);
      String error = String.valueOf(e.getMessage());
      jdbc.update(
          "UPDATE engine.outbox SET attempts = attempts + 1, last_error = ?, "
              + "next_attempt_at = CASE WHEN attempts + 1 >= ? THEN 'infinity'::timestamptz "
              + "ELSE now() + make_interval(secs => LEAST(300, power(2, attempts))) END WHERE id = ?",
          error.substring(0, Math.min(ERROR_LENGTH, error.length())),
          maxAttempts,
          id);
      return reachable;
    }
  }
}
