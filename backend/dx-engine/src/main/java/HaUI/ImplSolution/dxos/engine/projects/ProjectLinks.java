// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.projects;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// The project each model and each outside application belongs to. A model or a client with no row
// here is unclassified; the link is only ever written by an administrator.
@Component
public class ProjectLinks {

  private final JdbcTemplate jdbc;

  public ProjectLinks(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // Model name to project name.
  public Map<String, String> ofModels() {
    return pairs("SELECT model AS k, project AS v FROM engine.model_project");
  }

  // Keycloak client id to project name.
  public Map<String, String> ofClients() {
    return pairs("SELECT client_id AS k, project AS v FROM engine.client_project");
  }

  public boolean exists(String project) {
    Integer found =
        jdbc.queryForObject(
            "SELECT count(*) FROM engine.project WHERE name = ?", Integer.class, project);
    return found != null && found > 0;
  }

  // A null project makes the model unclassified.
  public void setModel(String model, String project) {
    if (project == null) {
      jdbc.update("DELETE FROM engine.model_project WHERE model = ?", model);
      return;
    }
    jdbc.update(
        "INSERT INTO engine.model_project (model, project) VALUES (?, ?) "
            + "ON CONFLICT (model) DO UPDATE SET project = EXCLUDED.project",
        model,
        project);
  }

  // A null project makes the client unclassified.
  public void setClient(String clientId, String project) {
    if (project == null) {
      dropClient(clientId);
      return;
    }
    jdbc.update(
        "INSERT INTO engine.client_project (client_id, project) VALUES (?, ?) "
            + "ON CONFLICT (client_id) DO UPDATE SET project = EXCLUDED.project",
        clientId,
        project);
  }

  public void dropClient(String clientId) {
    jdbc.update("DELETE FROM engine.client_project WHERE client_id = ?", clientId);
  }

  private Map<String, String> pairs(String sql) {
    Map<String, String> out = new LinkedHashMap<>();
    jdbc.queryForList(sql).forEach(row -> out.put((String) row.get("k"), (String) row.get("v")));
    return out;
  }
}
