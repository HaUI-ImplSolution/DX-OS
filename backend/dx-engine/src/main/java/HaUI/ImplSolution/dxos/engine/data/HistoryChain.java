// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Links each history line to the line before it with a SHA-256 hash (engine.record_event_hash), so
// a line that is changed, removed or slipped in later breaks the chain. Writers take one lock for
// the rest of their transaction before they insert, so lines are linked in the order of their ids.
@Component
public class HistoryChain {

  private static final Logger log = LoggerFactory.getLogger(HistoryChain.class);
  // Key of the advisory lock that makes writers of the history take turns.
  private static final long LOCK = 0x6478_6F73_6869_7374L;
  private static final int BATCH = 1000;

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;

  public HistoryChain(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(transactions);
  }

  // Must be called in the transaction that writes the lines, before the first insert.
  public void lock() {
    jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", LOCK);
  }

  // Links the line with this id to the one before it and returns its hash.
  public String link(long id) {
    return jdbc.queryForObject(
        "UPDATE engine.record_event e SET prev_hash = p.hash, "
            + "hash = engine.record_event_hash(p.hash, e) FROM (SELECT (SELECT hash FROM "
            + "engine.record_event WHERE id < ? ORDER BY id DESC LIMIT 1) AS hash) p "
            + "WHERE e.id = ? RETURNING e.hash",
        String.class,
        id,
        id);
  }

  // Links the lines written before the chain existed.
  @EventListener(ApplicationReadyEvent.class)
  public void linkUnlinked() {
    int linked = 0;
    while (true) {
      Integer done =
          transaction.execute(
              status -> {
                lock();
                List<Long> ids =
                    jdbc.queryForList(
                        "SELECT id FROM engine.record_event WHERE hash IS NULL ORDER BY id LIMIT "
                            + BATCH,
                        Long.class);
                ids.forEach(this::link);
                return ids.size();
              });
      linked += done == null ? 0 : done;
      if (done == null || done < BATCH) {
        break;
      }
    }
    if (linked > 0) {
      log.info("Linked {} history lines written before the chain", linked);
    }
  }

  // The first line where the chain breaks, or null when every line is intact.
  public Map<String, Object> firstBreak() {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT id, CASE WHEN hash IS NULL THEN 'not linked' "
                + "WHEN prev_hash IS DISTINCT FROM before THEN 'the line before it was changed or removed' "
                + "ELSE 'its content was changed' END AS reason FROM (SELECT e.id, e.hash, e.prev_hash, "
                + "lag(e.hash) OVER (ORDER BY e.id) AS before, "
                + "engine.record_event_hash(e.prev_hash, e) AS expected FROM engine.record_event e) c "
                + "WHERE hash IS NULL OR prev_hash IS DISTINCT FROM before OR hash <> expected "
                + "ORDER BY id LIMIT 1");
    return rows.isEmpty() ? null : rows.get(0);
  }

  // The last line of the chain (id and hash), or null when there is no history yet.
  public Map<String, Object> head() {
    List<Map<String, Object>> rows =
        jdbc.queryForList("SELECT id, hash, at FROM engine.record_event ORDER BY id DESC LIMIT 1");
    return rows.isEmpty() ? null : rows.get(0);
  }

  // The hash stored for a line, or null when there is no such line.
  public String hashOf(long id) {
    List<String> rows =
        jdbc.queryForList("SELECT hash FROM engine.record_event WHERE id = ?", String.class, id);
    return rows.isEmpty() ? null : rows.get(0);
  }
}
