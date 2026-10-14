// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.scheduling;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// A PostgreSQL advisory lock keyed by the job name. It is held on one connection for the length
// of the job, so a crashed instance releases it when its connection closes.
@Component
public class AdvisoryJobLock implements JobLock {

  private final JdbcTemplate jdbcTemplate;

  public AdvisoryJobLock(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public boolean runIfFree(String name, Runnable job) {
    return Boolean.TRUE.equals(jdbcTemplate.execute((java.sql.Connection connection) -> {
      if (!call(connection, "SELECT pg_try_advisory_lock(hashtext(?))", name)) {
        return false;
      }
      try {
        job.run();
        return true;
      } finally {
        call(connection, "SELECT pg_advisory_unlock(hashtext(?))", name);
      }
    }));
  }

  private static boolean call(java.sql.Connection connection, String sql, String name)
      throws java.sql.SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, "dxos.job." + name);
      try (ResultSet result = statement.executeQuery()) {
        return result.next() && result.getBoolean(1);
      }
    }
  }
}
