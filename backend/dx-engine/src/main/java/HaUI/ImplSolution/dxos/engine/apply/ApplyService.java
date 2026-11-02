// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Role;
import HaUI.ImplSolution.dxos.model.ModelParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// Turns a model file into a schema, roles and a stored version, step by step, and records each
// step. A step that ran can run again, so an interrupted apply is finished by applying again.
@Service
public class ApplyService {

  public record PlannedStep(String kind, String target) {}

  public record Plan(String model, int version, String action, List<PlannedStep> steps) {}

  public record Applied(String model, int version, String status, long runId,
      List<PlannedStep> steps) {}

  private interface Action {
    void run();
  }

  private record Step(PlannedStep info, Action action) {}

  private static final int ERROR_LENGTH = 500;

  private final JdbcTemplate jdbc;
  private final KeycloakAdminClient keycloak;
  private final ModelRegistry registry;
  private final String reportPassword;

  public ApplyService(JdbcTemplate jdbc, KeycloakAdminClient keycloak, ModelRegistry registry,
      @Value("${engine.report-db-password:}") String reportPassword) {
    this.jdbc = jdbc;
    this.keycloak = keycloak;
    this.registry = registry;
    this.reportPassword = reportPassword;
  }

  public Plan plan(String content) {
    Model model = ModelParser.parse(content);
    String hash = hash(content);
    Decision decision = decide(model, hash);
    if (decision.unchanged()) {
      return new Plan(model.name(), decision.version(), "unchanged", List.of());
    }
    return new Plan(model.name(), decision.version(), "create",
        steps(model, content, hash, "plan").stream().map(Step::info).toList());
  }

  public Applied apply(String content, String actor) {
    Model model = ModelParser.parse(content);
    return exclusively(model, () -> run(model, content, actor));
  }

  private Applied run(Model model, String content, String actor) {
    String hash = hash(content);
    Decision decision = decide(model, hash);
    if (decision.unchanged()) {
      return new Applied(model.name(), decision.version(), "unchanged", 0, List.of());
    }
    long runId = jdbc.queryForObject(
        "INSERT INTO engine.apply_run (model, hash, status, actor) VALUES (?, ?, 'running', ?) "
            + "RETURNING id",
        Long.class, model.name(), hash, actor);
    List<Step> steps = steps(model, content, hash, actor);
    int seq = 0;
    for (Step step : steps) {
      seq++;
      try {
        step.action().run();
        recordStep(runId, seq, step.info(), "done", null);
      } catch (RuntimeException e) {
        recordStep(runId, seq, step.info(), "failed", e.getMessage());
        jdbc.update(
            "UPDATE engine.apply_run SET status = 'failed', finished_at = now() " + "WHERE id = ?",
            runId);
        throw e;
      }
    }
    jdbc.update("UPDATE engine.apply_run SET status = 'done', finished_at = now() WHERE id = ?",
        runId);
    registry.reload();
    return new Applied(model.name(), decision.version(), "applied", runId,
        steps.stream().map(Step::info).toList());
  }

  public List<Map<String, Object>> models() {
    return jdbc.queryForList("SELECT DISTINCT ON (model) model, version, hash, applied_by, "
        + "applied_at FROM engine.model_version ORDER BY model, version DESC");
  }

  public List<Map<String, Object>> runs(String model) {
    return jdbc.queryForList("SELECT r.id, r.status, r.actor, r.started_at, r.finished_at, "
        + "(SELECT coalesce(jsonb_agg(jsonb_build_object('seq', s.seq, 'kind', s.kind, 'target', "
        + "s.target, 'status', s.status, 'error', s.error) ORDER BY s.seq), '[]'::jsonb)::text "
        + "FROM engine.apply_step s WHERE s.run_id = r.id) AS steps FROM engine.apply_run r "
        + "WHERE r.model = ? ORDER BY r.id DESC LIMIT 20", model);
  }

  private record Decision(boolean unchanged, int version) {}

  // One apply of a model at a time. The lock lives on its own connection, so it ends with the
  // connection if the service dies, while every step commits on its own.
  private <T> T exclusively(Model model, Supplier<T> work) {
    String key = "dxos.apply." + model.name();
    return jdbc.execute((ConnectionCallback<T>) connection -> {
      if (!advisoryLock(connection, "pg_try_advisory_lock", key)) {
        throw new InvalidStateException("Model " + model.name() + " is being applied right now");
      }
      try {
        return work.get();
      } finally {
        advisoryLock(connection, "pg_advisory_unlock", key);
      }
    });
  }

  private static boolean advisoryLock(Connection connection, String function, String key)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT " + function + "(hashtext(?))")) {
      statement.setString(1, key);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getBoolean(1);
      }
    }
  }

  // The first apply of a model creates version 1; the same content again changes nothing. An
  // apply that stopped midway left tables behind, so only its own file may finish it.
  private Decision decide(Model model, String hash) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT version, hash FROM engine.model_version WHERE model = ? ORDER BY version DESC "
            + "LIMIT 1",
        model.name());
    if (rows.isEmpty()) {
      requireSameFileAsUnfinishedApply(model, hash);
      return new Decision(false, 1);
    }
    int version = ((Number) rows.get(0).get("version")).intValue();
    if (hash.equals(rows.get(0).get("hash"))) {
      return new Decision(true, version);
    }
    throw new InvalidStateException("Model " + model.name() + " is already applied with other "
        + "content; changing an applied model is not supported yet");
  }

  private void requireSameFileAsUnfinishedApply(Model model, String hash) {
    Integer schemas = jdbc.queryForObject(
        "SELECT count(*) FROM information_schema.schemata WHERE schema_name = ?", Integer.class,
        model.schema());
    if (schemas == null || schemas == 0) {
      return;
    }
    List<String> last = jdbc.queryForList(
        "SELECT hash FROM engine.apply_run WHERE model = ? ORDER BY id DESC LIMIT 1", String.class,
        model.name());
    if (last.isEmpty() || !hash.equals(last.get(0))) {
      throw new InvalidStateException("An earlier apply of model " + model.name() + " stopped "
          + "midway with other content; apply that file again, or drop schema " + model.schema()
          + " first");
    }
  }

  private List<Step> steps(Model model, String content, String hash, String actor) {
    List<Step> steps = new ArrayList<>();
    steps.add(sql("CREATE_SCHEMA", model.schema(), DdlGenerator.createSchema(model)));
    for (Entity entity : model.entities().values()) {
      steps.add(sql("CREATE_TABLE", entity.name(), DdlGenerator.createTable(model, entity)));
    }
    for (Entity entity : model.entities().values()) {
      for (String statement : DdlGenerator.links(model, entity)) {
        steps.add(sql("ADD_LINKS", entity.name(), statement));
      }
    }
    for (String statement : DdlGenerator.readOnlyAccess(model, reportPassword)) {
      steps.add(sql("GRANT_READ_ONLY", DdlGenerator.REPORT_ROLE, statement));
    }
    for (Role role : model.roles()) {
      steps.add(new Step(new PlannedStep("ENSURE_ROLE", role.name()),
          () -> keycloak.ensureRealmRole(role.name(), role.description())));
    }
    steps.add(new Step(new PlannedStep("SAVE_VERSION", model.name()),
        () -> jdbc.update(
            "INSERT INTO engine.model_version (model, version, hash, content, applied_by) "
                + "VALUES (?, 1, ?, ?, ?) ON CONFLICT (model, version) DO NOTHING",
            model.name(), hash, content, actor)));
    return steps;
  }

  private Step sql(String kind, String target, String statement) {
    return new Step(new PlannedStep(kind, target), () -> jdbc.execute(statement));
  }

  private void recordStep(long runId, int seq, PlannedStep step, String status, String error) {
    String message =
        error == null ? null : error.substring(0, Math.min(error.length(), ERROR_LENGTH));
    jdbc.update("INSERT INTO engine.apply_step (run_id, seq, kind, target, status, error) "
        + "VALUES (?, ?, ?, ?, ?, ?)", runId, seq, step.kind(), step.target(), status, message);
  }

  static String hash(String content) {
    try {
      String normalized = content.replace("\r\n", "\n").strip();
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
