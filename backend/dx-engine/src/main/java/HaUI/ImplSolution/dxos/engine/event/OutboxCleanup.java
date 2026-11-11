// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Deletes, in batches, the messages of the outbox that were delivered more than the set number of
// days ago (at least one). A message that is still waiting stays whatever its age.
@Component
public class OutboxCleanup {

  private final JdbcTemplate jdbc;
  private final JobLock lock;
  private static final int BATCH = 1000;

  private final int retentionDays;

  public OutboxCleanup(
      JdbcTemplate jdbc,
      JobLock lock,
      @Value("${engine.outbox.retention-days:30}") int retentionDays) {
    this.jdbc = jdbc;
    this.lock = lock;
    this.retentionDays = Math.max(1, retentionDays);
  }

  @Scheduled(fixedDelayString = "${engine.outbox.cleanup-ms:3600000}")
  public void run() {
    lock.runIfFree("engine-outbox-cleanup", this::clean);
  }

  int clean() {
    int total = 0;
    int deleted;
    do {
      deleted =
          jdbc.update(
              "DELETE FROM engine.outbox WHERE id IN (SELECT id FROM engine.outbox "
                  + "WHERE delivered_at < now() - make_interval(days => ?) ORDER BY id LIMIT "
                  + BATCH
                  + ")",
              retentionDays);
      total += deleted;
    } while (deleted == BATCH);
    return total;
  }
}
