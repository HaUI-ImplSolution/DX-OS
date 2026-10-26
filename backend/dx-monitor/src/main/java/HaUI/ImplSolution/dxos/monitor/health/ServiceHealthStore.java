// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.health;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// The UP/DOWN changes of the probed services.
@Repository
public class ServiceHealthStore {

  private final JdbcTemplate jdbc;

  public ServiceHealthStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // The last recorded state of each service.
  public Map<String, String> lastStates() {
    Map<String, String> states = new HashMap<>();
    jdbc.query("""
        SELECT DISTINCT ON (service) service, state
        FROM service_health_event
        ORDER BY service, occurred_at DESC, id DESC
        """, rs -> {
      states.put(rs.getString("service"), rs.getString("state"));
    });
    return states;
  }

  public void record(String service, String state, String detail, Instant at) {
    jdbc.update(
        "INSERT INTO service_health_event (occurred_at, service, state, detail) VALUES (?, ?, ?, ?)",
        Timestamp.from(at), service, state, detail);
  }

  public int deleteOlderThan(Duration age) {
    return jdbc.update("DELETE FROM service_health_event WHERE occurred_at < ?",
        Timestamp.from(Instant.now().minus(age)));
  }
}
