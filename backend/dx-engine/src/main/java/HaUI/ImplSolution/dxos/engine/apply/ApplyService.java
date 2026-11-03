// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.dxos.engine.apply.ModelDiff.Change;
import HaUI.ImplSolution.dxos.engine.apply.ModelDiff.Level;
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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// Brings the system to what a model file declares, from nothing or from the running version, step
// by step, and records each step. A step that ran can run again, so an interrupted apply is
// finished
// by applying again. The comparison of the two models decides the risk; the schema tool writes the
// SQL, and it does not run if the two disagree about what loses data.
@Service
public class ApplyService {

  public record PlannedStep(String kind, String target) {}

  // Rows that break a new rule, with some of their ids.
  public record Violation(String entity, String field, String rule, long rows, List<Long> sample) {}

  public record Plan(String model, int fromVersion, int version, String action, Level level,
      List<Change> changes, List<String> rejections, List<String> questions,
      List<Violation> violations, Map<String, Long> openRecords, List<String> statements) {}

  public record Applied(String model, int version, String status, long runId,
      List<PlannedStep> steps) {}

  private interface Action {
    void run();
  }

  private record Step(PlannedStep info, Action action) {}

  private record Running(int version, String hash, Model model) {}

  private static final int ERROR_LENGTH = 500;
  private static final int SAMPLE_SIZE = 20;
  private static final String RENAME_STEP = "RENAME_FIELD";
  private static final Set<String> PROCESS_RULE_CHANGES =
      Set.of("ADD_PROCESS", "DROP_PROCESS", "CHANGE_HANDLER", "CHANGE_SELF_DECISION",
          "CHANGE_COMPLETION", "CHANGE_BOOKING", "CHANGE_DEADLINE");

  private final JdbcTemplate jdbc;
  private final KeycloakAdminClient keycloak;
  private final ModelRegistry registry;
  private final SchemaTool schemaTool;
  private final String reportPassword;

  public ApplyService(JdbcTemplate jdbc, KeycloakAdminClient keycloak, ModelRegistry registry,
      SchemaTool schemaTool, @Value("${engine.report-db-password:}") String reportPassword) {
    this.jdbc = jdbc;
    this.keycloak = keycloak;
    this.registry = registry;
    this.schemaTool = schemaTool;
    this.reportPassword = reportPassword;
  }

  public Plan plan(String content) {
    Model model = ModelParser.parse(content);
    return plan(model, hash(content), Map.of());
  }

  // What a return to the version before the running one would do.
  public Plan planRollback(String modelName) {
    Previous previous = previous(modelName);
    return plan(previous.model(), hash(previous.content()), previous.renames());
  }

  // Who applies a model: the token "sub" and the name shown in the version history.
  public record Actor(String id, String name) {}

  public Applied apply(String content, Actor actor, Integer base, Level accept) {
    Model model = ModelParser.parse(content);
    return exclusively(model,
        () -> run(model, content, actor, base, accept == null ? Level.SAFE : accept, Map.of()));
  }

  public Applied rollback(String modelName, Actor actor, Integer base, Level accept) {
    Previous previous = previous(modelName);
    return exclusively(previous.model(), () -> run(previous.model(), previous.content(), actor,
        base, accept == null ? Level.SAFE : accept, previous.renames()));
  }

  private Plan plan(Model model, String hash, Map<String, Map<String, String>> renames) {
    Running running = running(model.name());
    requireResumable(model, hash);
    int from = running == null ? 0 : running.version();
    if (isUnchanged(model, hash, running)) {
      return new Plan(model.name(), from, from, "unchanged", Level.SAFE, List.of(), List.of(),
          List.of(), List.of(), Map.of(), List.of());
    }
    ModelDiff diff = ModelDiff.between(running == null ? null : running.model(), model, renames);
    List<String> rejections = new ArrayList<>(diff.rejections);
    rejections.addAll(roleCollisions(model));
    Map<String, Long> openRecords = openRecords(running, diff);
    rejections.addAll(processRuleChangesWithOpenRecords(diff, openRecords));
    rejections.addAll(dropDependencyRejections(model, diff));
    List<Violation> violations = List.of();
    List<String> statements = List.of();
    if (rejections.isEmpty()) {
      prepareSchema(model);
      violations = violations(model, running, diff);
    }
    if (rejections.isEmpty()) {
      SchemaTool.Plan schemaPlan =
          schemaTool.plan(model, DdlGenerator.desiredState(model, pendingRenames(model, diff)));
      rejections.addAll(unexpectedDrops(diff, schemaPlan));
      statements = new ArrayList<>();
      for (Map.Entry<String, Map<String, String>> entity : diff.renames.entrySet()) {
        for (Map.Entry<String, String> field : entity.getValue().entrySet()) {
          statements
              .add("RENAME " + entity.getKey() + "." + field.getValue() + " TO " + field.getKey());
        }
      }
      for (SchemaTool.Statement statement : schemaPlan.statements()) {
        statements.add(statement.sql());
      }
    }
    String action = !rejections.isEmpty() || !violations.isEmpty() ? "rejected"
        : running == null ? "create" : "change";
    return new Plan(model.name(), from, nextVersion(hash, running), action, diff.level(),
        diff.changes, rejections, diff.questions, violations, openRecords, statements);
  }

  private Applied run(Model model, String content, Actor actor, Integer base, Level accept,
      Map<String, Map<String, String>> renames) {
    String hash = hash(content);
    Running running = running(model.name());
    requireResumable(model, hash);
    if (isUnchanged(model, hash, running)) {
      return new Applied(model.name(), running.version(), "unchanged", 0, List.of());
    }
    if (running != null && !Objects.equals(base, running.version())) {
      throw new InvalidStateException(
          "The plan is out of date: model " + model.name() + " runs version " + running.version()
              + "; make a plan and pass its fromVersion as base");
    }
    ModelDiff diff = ModelDiff.between(running == null ? null : running.model(), model, renames);
    if (!diff.rejections.isEmpty()) {
      throw new InvalidStateException(String.join("; ", diff.rejections));
    }
    List<String> roleCollisions = roleCollisions(model);
    if (!roleCollisions.isEmpty()) {
      throw new InvalidStateException(String.join("; ", roleCollisions));
    }
    List<String> dropRejections = dropDependencyRejections(model, diff);
    if (!dropRejections.isEmpty()) {
      throw new InvalidStateException(String.join("; ", dropRejections));
    }
    if (diff.level().compareTo(accept) > 0) {
      throw new InvalidStateException("This change is at level " + diff.level()
          + "; read the plan and pass accept=" + diff.level());
    }
    prepareSchema(model);
    List<Violation> violations = violations(model, running, diff);
    if (!violations.isEmpty()) {
      Violation first = violations.get(0);
      throw new InvalidStateException(
          violations.size() + " rules are broken by existing rows, first " + first.entity() + "."
              + first.field() + " with " + first.rows() + " rows; see the plan");
    }
    List<String> processRejections =
        processRuleChangesWithOpenRecords(diff, openRecords(running, diff));
    if (!processRejections.isEmpty()) {
      throw new InvalidStateException(String.join("; ", processRejections));
    }
    int version = nextVersion(hash, running);
    long runId = jdbc.queryForObject(
        "INSERT INTO engine.apply_run (model, hash, status, actor) VALUES (?, ?, 'running', ?) "
            + "RETURNING id",
        Long.class, model.name(), hash, actor.id());
    List<Step> steps = steps(model, diff, content, hash, actor, version);
    registry.beginApply(model.name());
    try {
      int seq = 0;
      for (Step step : steps) {
        seq++;
        try {
          step.action().run();
          recordStep(runId, seq, step.info(), "done", null);
        } catch (RuntimeException e) {
          recordStep(runId, seq, step.info(), "failed", e.getMessage());
          throw e;
        }
      }
      registry.reload();
      jdbc.update("UPDATE engine.apply_run SET status = 'done', finished_at = now() WHERE id = ?",
          runId);
      registry.endApply(model.name());
      return new Applied(model.name(), version, "applied", runId,
          steps.stream().map(Step::info).toList());
    } catch (RuntimeException e) {
      jdbc.update(
          "UPDATE engine.apply_run SET status = 'failed', finished_at = now() " + "WHERE id = ?",
          runId);
      throw e;
    }
  }

  private List<String> roleCollisions(Model model) {
    Set<String> roles = model.roles().stream().map(Role::name).collect(Collectors.toSet());
    if (roles.isEmpty()) {
      return List.of();
    }
    List<String> collisions = new ArrayList<>();
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT DISTINCT ON (model) model, content FROM engine.model_version WHERE model <> ? "
            + "ORDER BY model, version DESC",
        model.name());
    for (Map<String, Object> row : rows) {
      Model other = ModelParser.parse((String) row.get("content"));
      other.roles().stream().map(Role::name).filter(roles::contains).distinct().forEach(
          role -> collisions.add("Role " + role + " is already used by model " + row.get("model")));
    }
    return collisions;
  }

  private static List<String> processRuleChangesWithOpenRecords(ModelDiff diff,
      Map<String, Long> openRecords) {
    return diff.changes.stream().filter(change -> PROCESS_RULE_CHANGES.contains(change.kind()))
        .filter(change -> openRecords.containsKey(change.entity()))
        .map(change -> "Cannot change " + change.kind() + " for " + change.entity() + " while "
            + openRecords.get(change.entity()) + " records are still open")
        .distinct().toList();
  }

  private List<String> dropDependencyRejections(Model model, ModelDiff diff) {
    List<String> rejections = new ArrayList<>();
    diff.changes.stream().filter(change -> "DROP_ENTITY".equals(change.kind())).forEach(change -> {
      Long attachments = jdbc.queryForObject(
          "SELECT count(*) FROM engine.attachment WHERE model = ? AND entity = ?", Long.class,
          model.name(), change.entity());
      Long processes = jdbc.queryForObject(
          "SELECT count(DISTINCT proc_inst_id_) FROM engine.act_ru_execution "
              + "WHERE business_key_ LIKE ?",
          Long.class, model.name() + "." + change.entity() + ".%");
      if ((attachments != null && attachments > 0) || (processes != null && processes > 0)) {
        rejections.add("Cannot drop " + change.entity() + " while it has "
            + (processes == null ? 0 : processes) + " process instances and "
            + (attachments == null ? 0 : attachments) + " attachments");
      }
    });
    return rejections;
  }

  public List<Map<String, Object>> models() {
    return jdbc.queryForList("SELECT DISTINCT ON (model) model, version, hash, applied_by, "
        + "applied_by_name, applied_at FROM engine.model_version ORDER BY model, version DESC");
  }

  public List<Map<String, Object>> runs(String model) {
    return jdbc.queryForList("SELECT r.id, r.status, r.actor, r.started_at, r.finished_at, "
        + "(SELECT coalesce(jsonb_agg(jsonb_build_object('seq', s.seq, 'kind', s.kind, 'target', "
        + "s.target, 'status', s.status, 'error', s.error) ORDER BY s.seq), '[]'::jsonb)::text "
        + "FROM engine.apply_step s WHERE s.run_id = r.id) AS steps FROM engine.apply_run r "
        + "WHERE r.model = ? ORDER BY r.id DESC LIMIT 20", model);
  }

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

  private record Previous(Model model, String content, Map<String, Map<String, String>> renames) {}

  // The version before the running one, with the renames that led away from it turned around.
  private Previous previous(String modelName) {
    Running running = running(modelName);
    List<String> contents = running == null ? List.of()
        : jdbc.queryForList(
            "SELECT content FROM engine.model_version WHERE model = ? AND version = ?",
            String.class, modelName, running.version() - 1);
    if (contents.isEmpty()) {
      throw new InvalidStateException("Model " + modelName + " has no earlier version");
    }
    String content = contents.get(0);
    Model target = ModelParser.parse(content);
    Map<String, Map<String, String>> reversed = new LinkedHashMap<>();
    ModelDiff.between(target, running.model(), Map.of()).renames.forEach((entity, fields) -> {
      Map<String, String> back = new LinkedHashMap<>();
      fields.forEach((now, before) -> back.put(before, now));
      reversed.put(entity, back);
    });
    return new Previous(target, content, reversed);
  }

  // What the schema tool needs to exist before it can compare anything; harmless to repeat.
  private void prepareSchema(Model model) {
    jdbc.execute(DdlGenerator.createSchema(model));
    jdbc.execute(DdlGenerator.reportRole(reportPassword));
    jdbc.execute(DdlGenerator.grantSchemaUsage(model));
  }

  private Running running(String modelName) {
    List<Map<String, Object>> rows =
        jdbc.queryForList("SELECT version, hash, content FROM engine.model_version WHERE model = ? "
            + "ORDER BY version DESC LIMIT 1", modelName);
    if (rows.isEmpty()) {
      return null;
    }
    Map<String, Object> row = rows.get(0);
    return new Running(((Number) row.get("version")).intValue(), (String) row.get("hash"),
        ModelParser.parse((String) row.get("content")));
  }

  private Optional<Map<String, Object>> lastRun(String modelName) {
    return jdbc.queryForList(
        "SELECT id, hash, status FROM engine.apply_run WHERE model = ? ORDER BY id DESC LIMIT 1",
        modelName).stream().findFirst();
  }

  private boolean isUnchanged(Model model, String hash, Running running) {
    boolean settled =
        lastRun(model.name()).map(run -> "done".equals(run.get("status"))).orElse(true);
    return running != null && settled && hash.equals(running.hash());
  }

  private static int nextVersion(String hash, Running running) {
    if (running == null) {
      return 1;
    }
    return hash.equals(running.hash()) ? running.version() : running.version() + 1;
  }

  // The schema tool brings the tables to any target, except that a rename it did not see would
  // look like a dropped column. So once a stopped apply has renamed something, only its own file
  // may finish it.
  private void requireResumable(Model model, String hash) {
    Optional<Map<String, Object>> last = lastRun(model.name());
    if (last.isEmpty() || "done".equals(last.get().get("status"))
        || hash.equals(last.get().get("hash"))) {
      return;
    }
    Integer renamed = jdbc.queryForObject(
        "SELECT count(*) FROM engine.apply_step WHERE run_id = ? AND kind = ? AND status = 'done'",
        Integer.class, last.get().get("id"), RENAME_STEP);
    if (renamed != null && renamed > 0) {
      throw new InvalidStateException("An earlier apply of model " + model.name()
          + " stopped after renaming fields; apply that file again to finish it");
    }
  }

  // The renames that have not run yet, by entity: new field name to the column it still is.
  private Map<String, Map<String, String>> pendingRenames(Model model, ModelDiff diff) {
    Map<String, Map<String, String>> pending = new LinkedHashMap<>();
    diff.renames.forEach((entity, fields) -> {
      Set<String> columns = columns(model, entity);
      Map<String, String> left = new LinkedHashMap<>();
      fields.forEach((name, from) -> {
        if (columns.contains(from)) {
          left.put(name, from);
        }
      });
      pending.put(entity, left);
    });
    return pending;
  }

  private Set<String> columns(Model model, String entity) {
    return new HashSet<>(jdbc.queryForList(
        "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?",
        String.class, model.schema(), entity));
  }

  // The plan of the tool and the comparison of the model must agree on what loses data.
  private static List<String> unexpectedDrops(ModelDiff diff, SchemaTool.Plan plan) {
    return plan.statements().stream()
        .filter(statement -> statement.dropsData() && !diff.allowedDrops.contains(statement.path()))
        .map(statement -> "The schema tool would remove " + statement.path()
            + ", which the model does not ask for")
        .toList();
  }

  private List<Violation> violations(Model model, Running running, ModelDiff diff) {
    List<Violation> violations = new ArrayList<>();
    if (running == null) {
      return violations;
    }
    for (ModelDiff.RowCheck check : diff.checks) {
      Entity entity = running.model().entities().get(check.entity());
      Set<String> columns = columns(model, check.entity());
      if (columns.isEmpty()) {
        continue;
      }
      String table = Sql.ident(model.schema()) + "." + Sql.ident(check.entity());
      String column = check.column() == null ? null
          : columns.contains(check.column()) ? check.column() : check.field();
      String condition = switch (check.rule()) {
        case NEW_REQUIRED -> "TRUE";
        case REQUIRED -> column == null ? "TRUE" : Sql.ident(column) + " IS NULL";
        case UNIQUE -> Sql.ident(column) + " IN (SELECT " + Sql.ident(column) + " FROM " + table
            + " GROUP BY 1 HAVING count(*) > 1)";
        case OPTIONS -> Sql.ident(column) + " NOT IN ("
            + check.options().stream().map(Sql::literal).collect(Collectors.joining(", ")) + ")";
      };
      Long rows =
          jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + condition, Long.class);
      if (rows != null && rows > 0) {
        List<Long> sample = jdbc.queryForList(
            "SELECT id FROM " + table + " WHERE " + condition + " ORDER BY id LIMIT " + SAMPLE_SIZE,
            Long.class);
        violations
            .add(new Violation(entity.name(), check.field(), check.rule().name(), rows, sample));
      }
    }
    return violations;
  }

  // Records of the touched entities still moving through their process.
  private Map<String, Long> openRecords(Running running, ModelDiff diff) {
    Map<String, Long> open = new LinkedHashMap<>();
    if (running == null) {
      return open;
    }
    for (String name : diff.touchedProcesses) {
      Entity entity = running.model().entities().get(name);
      if (entity == null || entity.process() == null) {
        continue;
      }
      String states =
          entity.process().definition().states().stream().filter(state -> !state.terminal())
              .map(state -> Sql.literal(state.name())).collect(Collectors.joining(", "));
      Long rows = jdbc.queryForObject("SELECT count(*) FROM " + Sql.table(running.model(), entity)
          + " WHERE status IN (" + states + ")", Long.class);
      if (rows != null && rows > 0) {
        open.put(name, rows);
      }
    }
    return open;
  }

  private List<Step> steps(Model model, ModelDiff diff, String content, String hash, Actor actor,
      int version) {
    List<Step> steps = new ArrayList<>();
    diff.renames.forEach((entityName, fields) -> {
      Entity entity = model.entities().get(entityName);
      fields.forEach((name, from) -> steps.add(sql(RENAME_STEP, entityName + "." + name,
          DdlGenerator.renameField(model, entity, from, name))));
    });
    steps.add(new Step(new PlannedStep("CHANGE_SCHEMA", model.schema()), () -> {
      SchemaTool.Plan plan = schemaTool.plan(model, DdlGenerator.desiredState(model, Map.of()));
      List<String> unexpected = unexpectedDrops(diff, plan);
      if (!unexpected.isEmpty()) {
        throw new InvalidStateException(String.join("; ", unexpected));
      }
      schemaTool.apply(model, plan);
    }));
    for (Role role : model.roles()) {
      steps.add(new Step(new PlannedStep("ENSURE_ROLE", role.name()),
          () -> keycloak.ensureRealmRole(role.name(), role.description())));
    }
    for (Model.Unit unit : model.units()) {
      steps.add(new Step(new PlannedStep("ENSURE_UNIT", unit.name()),
          () -> keycloak.ensureGroup(unit.name())));
    }
    steps.add(new Step(new PlannedStep("SAVE_VERSION", model.name()), () -> jdbc.update(
        "INSERT INTO engine.model_version (model, version, hash, content, applied_by, "
            + "applied_by_name) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (model, version) DO NOTHING",
        model.name(), version, hash, content, actor.id(), actor.name())));
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
