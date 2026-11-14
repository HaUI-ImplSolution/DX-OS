// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Deletes, in batches, the Idempotency-Keys older than the set number of hours (at least one); a
// repeat of a request after that is done again.
@Component
public class IdempotencyCleanup {

  private static final int BATCH = 1000;

  private final JdbcTemplate jdbc;
  private final JobLock lock;
  private final int ttlHours;

  public IdempotencyCleanup(
      JdbcTemplate jdbc, JobLock lock, @Value("${engine.idempotency.ttl-hours:24}") int ttlHours) {
    this.jdbc = jdbc;
    this.lock = lock;
    this.ttlHours = Math.max(1, ttlHours);
  }

  @Scheduled(fixedDelayString = "${engine.idempotency.cleanup-ms:3600000}")
  public void run() {
    lock.runIfFree("engine-idempotency-cleanup", this::clean);
  }

  int clean() {
    int total = 0;
    int deleted;
    do {
      deleted =
          jdbc.update(
              "DELETE FROM engine.idempotency WHERE (subject, key) IN "
                  + "(SELECT subject, key FROM engine.idempotency "
                  + "WHERE created_at < now() - make_interval(hours => ?) LIMIT "
                  + BATCH
                  + ")",
              ttlHours);
      total += deleted;
    } while (deleted == BATCH);
    return total;
  }
}
