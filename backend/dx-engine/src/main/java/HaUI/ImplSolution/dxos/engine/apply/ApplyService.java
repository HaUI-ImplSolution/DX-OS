// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import HaUI.ImplSolution.dxos.core.api.audit.AuditEvent;
import HaUI.ImplSolution.dxos.core.api.audit.AuditRecorder;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.dxos.engine.apply.ModelDiff.Change;
import HaUI.ImplSolution.dxos.engine.apply.ModelDiff.Level;
import HaUI.ImplSolution.dxos.engine.event.AdminEventPublisher;
import HaUI.ImplSolution.dxos.engine.event.WebhookTargets;
import HaUI.ImplSolution.dxos.engine.files.AttachmentCleaner;
import HaUI.ImplSolution.dxos.engine.registry.ModelClient;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

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

  // What differs from the file that the running version was made from, and what in it cannot be
  // put right without losing data.
  private record Drift(List<Change> changes, List<String> rejections) {}

  // The last apply of a model, which stopped before it finished, with what it was asked to do.
  private record Stalled(String content, Map<String, Map<String, String>> renames, Level accept) {}

  private static final Logger log = LoggerFactory.getLogger(ApplyService.class);
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
  private final AttachmentCleaner attachments;
  private final ObjectMapper json;
  private final WebhookTargets webhooks;
  private final AdminEventPublisher events;
  private final AuditRecorder audit;
  private final String reportPassword;
  // Kind of step that fails on purpose, so that a stopped apply can be produced in tests.
  private final String faultStep;

  public ApplyService(JdbcTemplate jdbc, KeycloakAdminClient keycloak, ModelRegistry registry,
      SchemaTool schemaTool, AttachmentCleaner attachments, ObjectMapper json,
      WebhookTargets webhooks, AdminEventPublisher events, AuditRecorder audit,
      @Value("${engine.report-db-password:}") String reportPassword,
      @Value("${engine.fault.apply-step:}") String faultStep) {
    this.jdbc = jdbc;
    this.keycloak = keycloak;
    this.registry = registry;
    this.schemaTool = schemaTool;
    this.attachments = attachments;
    this.json = json;
    this.webhooks = webhooks;
    this.events = events;
    this.audit = audit;
    this.reportPassword = reportPassword;
    this.faultStep = faultStep.strip();
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
    return announced("MODEL_APPLIED", actor, exclusively(model,
        () -> run(model, content, actor, base, accept == null ? Level.SAFE : accept, Map.of())));
  }

  public Applied rollback(String modelName, Actor actor, Integer base, Level accept) {
    Previous previous = previous(modelName);
    return announced("MODEL_ROLLED_BACK", actor,
        exclusively(previous.model(), () -> run(previous.model(), previous.content(), actor, base,
            accept == null ? Level.SAFE : accept, previous.renames())));
  }

  // Writes an apply that changed something to the audit log and tells the webhook target about it.
  // A failure to queue the message does not undo the apply.
  private Applied announced(String kind, Actor actor, Applied result) {
    if ("unchanged".equals(result.status())) {
      return result;
    }
    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("version", result.version());
    facts.put("status", result.status());
    facts.put("runId", result.runId());
    Map<String, Object> metadata = new LinkedHashMap<>(facts);
    metadata.put("actorName", actor.name());
    audit.record(AuditEvent.success(kind, "MODEL", result.model(), metadata));
    try {
      events.publish(kind, result.model(), actor.id(), actor.name(), facts);
    } catch (RuntimeException e) {
      log.warn("The {} message of model {} could not be queued: {}", kind, result.model(),
          e.getMessage());
    }
    return result;
  }

  private Plan plan(Model model, String hash, Map<String, Map<String, String>> renames) {
    Running running = running(model.name());
    requireResumable(model, hash);
    int from = running == null ? 0 : running.version();
    List<Change> repairs = List.of();
    if (isUnchanged(model, hash, running)) {
      repairs = drift(model).changes();
      if (repairs.isEmpty()) {
        return new Plan(model.name(), from, from, "unchanged", Level.SAFE, List.of(), List.of(),
            List.of(), List.of(), Map.of(), List.of());
      }
    }
    ModelDiff diff = compare(running, model, renames);
    diff.changes.addAll(repairs);
    List<String> rejections = new ArrayList<>(diff.rejections);
    webhooks.missing(model).ifPresent(rejections::add);
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
      rejections.addAll(unexpectedDrops(diff.allowedDrops, schemaPlan));
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
        : running == null ? "create" : repairs.isEmpty() ? "change" : "repair";
    return new Plan(model.name(), from, nextVersion(hash, running), action, diff.level(),
        diff.changes, rejections, diff.questions, violations, openRecords, statements);
  }

  // A role that comes back to a model gives its access back to whoever still holds it in the
  // client of the model, so that role needs the same confirmation as moving data.
  private ModelDiff compare(Running running, Model model,
      Map<String, Map<String, String>> renames) {
    ModelDiff diff = ModelDiff.between(running == null ? null : running.model(), model, renames);
    String client = ModelClient.of(model.name());
    for (int i = 0; i < diff.changes.size(); i++) {
      Change change = diff.changes.get(i);
      if (!"ADD_ROLE".equals(change.kind())) {
        continue;
      }
      int holders = keycloak.usersWithClientRole(client, change.detail()).size();
      if (holders > 0) {
        diff.changes.set(i,
            new Change(Level.MIGRATION, change.kind(), null, null, change.detail()));
        diff.questions.add("role " + change.detail() + " is declared again and " + holders
            + " users still hold it; applying gives them its access back");
      }
    }
    return diff;
  }

  private Applied run(Model model, String content, Actor actor, Integer base, Level accept,
      Map<String, Map<String, String>> renames) {
    String hash = hash(content);
    Running running = running(model.name());
    requireResumable(model, hash);
    Drift drift = null;
    if (isUnchanged(model, hash, running)) {
      drift = drift(model);
      if (drift.changes().isEmpty()) {
        return new Applied(model.name(), running.version(), "unchanged", 0, List.of());
      }
      if (!drift.rejections().isEmpty()) {
        throw new InvalidStateException(String.join("; ", drift.rejections()));
      }
    }
    webhooks.missing(model).ifPresent(reason -> {
      throw new InvalidStateException(reason);
    });
    if (running != null && !Objects.equals(base, running.version())) {
      throw new InvalidStateException(
          "The plan is out of date: model " + model.name() + " runs version " + running.version()
              + "; make a plan and pass its fromVersion as base");
    }
    ModelDiff diff = compare(running, model, renames);
    if (drift != null) {
      diff.changes.addAll(drift.changes());
    }
    if (!diff.rejections.isEmpty()) {
      throw new InvalidStateException(String.join("; ", diff.rejections));
    }
    if (diff.level().compareTo(accept) > 0) {
      throw new InvalidStateException("This change is at level " + diff.level()
          + "; read the plan and pass accept=" + diff.level());
    }
    // The checks on the rows run with the model held, so no record is written between them and the
    // first step. A rejected apply lets the model go unless it was held before.
    boolean heldBefore = registry.isHeld(model.name());
    registry.beginApply(model.name());
    long runId;
    try {
      requireRows(model, running, diff);
      runId = startRun(model.name(), hash, actor, content, renames, accept);
    } catch (RuntimeException e) {
      if (!heldBefore) {
        registry.endApply(model.name());
      }
      throw e;
    }
    int version = nextVersion(hash, running);
    return execute(model.name(), runId, version, steps(model, diff, content, hash, actor, version));
  }

  // What the running tables and the Keycloak client no longer match of a model whose file did not
  // change: a column or index that is gone, a client, role or unit group that was removed by hand.
  // Nothing is changed here; the same steps of an apply put it right.
  private Drift drift(Model model) {
    List<Change> found = new ArrayList<>();
    prepareSchema(model);
    SchemaTool.Plan plan = schemaTool.plan(model, DdlGenerator.desiredState(model, Map.of()));
    for (SchemaTool.Statement statement : plan.statements()) {
      String[] path = statement.path().split("\\.");
      Level level = "create".equals(statement.operation()) ? Level.SAFE : Level.MIGRATION;
      found.add(new Change(level, "REPAIR_SCHEMA", path.length > 1 ? path[1] : null, null,
          statement.sql()));
    }
    String client = ModelClient.of(model.name());
    Optional<List<String>> existing = keycloak.clientRoleNames(client);
    if (existing.isEmpty()) {
      found.add(new Change(Level.SAFE, "REPAIR_CLIENT", null, null, client));
    }
    Set<String> held = new HashSet<>(existing.orElse(List.of()));
    for (Role role : model.roles()) {
      if (!held.contains(role.name())) {
        found.add(new Change(Level.SAFE, "REPAIR_ROLE", null, null, role.name()));
      }
    }
    for (Model.Unit unit : model.units()) {
      if (!keycloak.groupExists(unit.name())) {
        found.add(new Change(Level.SAFE, "REPAIR_UNIT", null, null, unit.name()));
      }
    }
    return new Drift(found, unexpectedDrops(Set.of(), plan));
  }

  private void requireRows(Model model, Running running, ModelDiff diff) {
    List<String> dropRejections = dropDependencyRejections(model, diff);
    if (!dropRejections.isEmpty()) {
      throw new InvalidStateException(String.join("; ", dropRejections));
    }
    List<String> processRejections =
        processRuleChangesWithOpenRecords(diff, openRecords(running, diff));
    if (!processRejections.isEmpty()) {
      throw new InvalidStateException(String.join("; ", processRejections));
    }
    prepareSchema(model);
    List<Violation> violations = violations(model, running, diff);
    if (!violations.isEmpty()) {
      Violation first = violations.get(0);
      throw new InvalidStateException(
          violations.size() + " rules are broken by existing rows, first " + first.entity() + "."
              + first.field() + " with " + first.rows() + " rows; see the plan");
    }
  }

  private long startRun(String model, String hash, Actor actor, String content,
      Map<String, Map<String, String>> renames, Level accept) {
    return jdbc.queryForObject(
        "INSERT INTO engine.apply_run (model, hash, status, actor, content, renames, accept) "
            + "VALUES (?, ?, 'running', ?, ?, ?, ?) RETURNING id",
        Long.class, model, hash, actor.id(), content, json.writeValueAsString(renames),
        accept.name());
  }

  // Runs the steps of an apply one after the other. The model is held from the first step on and
  // is let go when the registry has the new version; a step that fails leaves it held, because the
  // tables may be half changed, until the apply is finished or given up.
  private Applied execute(String model, long runId, int version, List<Step> steps) {
    registry.beginApply(model);
    try {
      int seq = 0;
      for (Step step : steps) {
        seq++;
        try {
          if (step.info().kind().equals(faultStep)) {
            throw new IllegalStateException("Fault injected at step " + faultStep);
          }
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
      registry.endApply(model);
      return new Applied(model, version, "applied", runId, steps.stream().map(Step::info).toList());
    } catch (RuntimeException e) {
      jdbc.update(
          "UPDATE engine.apply_run SET status = 'failed', finished_at = now() " + "WHERE id = ?",
          runId);
      throw e;
    }
  }

  // Finishes the apply that stopped, with the file, the renames and the level it was started with.
  public Applied resume(String modelName, Actor actor) {
    Stalled stalled = stalled(modelName);
    Model model = ModelParser.parse(stalled.content());
    return announced("MODEL_RESUMED", actor, exclusively(model, () -> {
      Running running = running(modelName);
      return run(model, stalled.content(), actor, running == null ? null : running.version(),
          stalled.accept(), stalled.renames());
    }));
  }

  // Gives up the apply that stopped: the tables go back to the running version. What the stopped
  // apply added goes with its rows; what it dropped does not come back.
  public Applied abandon(String modelName, Actor actor) {
    Running running = running(modelName);
    Stalled stalled = stalled(modelName);
    Model stopped = ModelParser.parse(stalled.content());
    if (running == null) {
      return announced("MODEL_ABANDONED", actor,
          exclusively(stopped, () -> removeUnfinished(stopped, stalled, actor)));
    }
    return announced("MODEL_ABANDONED", actor,
        exclusively(running.model(), () -> restore(running, stopped, actor)));
  }

  // The first apply of a model stopped, so there is no version to go back to: what it created goes,
  // the client with its roles too, so that nobody keeps a role of the model. Unit groups are shared
  // by every model and stay.
  private Applied removeUnfinished(Model stopped, Stalled stalled, Actor actor) {
    long runId = startRun(stopped.name(), hash(stalled.content()), actor, stalled.content(),
        Map.of(), Level.LOSS);
    String client = ModelClient.of(stopped.name());
    return execute(stopped.name(), runId, 0,
        List.of(
            new Step(new PlannedStep("DROP_SCHEMA", stopped.schema()),
                () -> jdbc
                    .execute("DROP SCHEMA IF EXISTS " + Sql.ident(stopped.schema()) + " CASCADE")),
            new Step(new PlannedStep("DROP_CLIENT", client), () -> keycloak.deleteClient(client))));
  }

  private Applied restore(Running running, Model stopped, Actor actor) {
    Model model = running.model();
    Map<String, Map<String, String>> back = new LinkedHashMap<>();
    ModelDiff.between(model, stopped, Map.of()).renames.forEach((entity, fields) -> {
      Map<String, String> turned = new LinkedHashMap<>();
      fields.forEach((now, before) -> turned.put(before, now));
      back.put(entity, turned);
    });
    ModelDiff diff = ModelDiff.between(stopped, model, back);
    String content = jdbc.queryForObject(
        "SELECT content FROM engine.model_version WHERE model = ? AND version = ?", String.class,
        model.name(), running.version());
    prepareSchema(model);
    long runId = startRun(model.name(), running.hash(), actor, content, back, Level.LOSS);
    return execute(model.name(), runId, running.version(),
        steps(model, diff, content, running.hash(), actor, running.version()));
  }

  private Stalled stalled(String modelName) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT status, content, renames, accept FROM engine.apply_run WHERE model = ? "
            + "ORDER BY id DESC LIMIT 1",
        modelName);
    if (rows.isEmpty() || "done".equals(rows.get(0).get("status"))) {
      throw new InvalidStateException("Model " + modelName + " has no apply that stopped");
    }
    Map<String, Object> row = rows.get(0);
    if (row.get("content") == null) {
      throw new InvalidStateException(
          "The apply of model " + modelName + " that stopped kept no file; apply the file again");
    }
    Map<String, Map<String, String>> renames = json.readValue((String) row.get("renames"),
        new TypeReference<Map<String, Map<String, String>>>() {});
    return new Stalled((String) row.get("content"), renames,
        Level.valueOf((String) row.get("accept")));
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
    // Records still moving through their process are work someone waits for, so they are finished
    // or withdrawn first. The files of the entity go with it when the change is applied.
    diff.changes.stream().filter(change -> "DROP_ENTITY".equals(change.kind())).forEach(change -> {
      Long processes = jdbc.queryForObject(
          "SELECT count(DISTINCT proc_inst_id_) FROM engine.act_ru_execution "
              + "WHERE business_key_ LIKE ? ESCAPE '\\'",
          Long.class, likeLiteral(model.name() + "." + change.entity() + ".") + "%");
      if (processes != null && processes > 0) {
        rejections.add("Cannot drop " + change.entity() + " while it has " + processes
            + " process instances running");
      }
    });
    return rejections;
  }

  public List<Map<String, Object>> models() {
    List<Map<String, Object>> models = jdbc.queryForList(
        "SELECT DISTINCT ON (model) model, version, hash, applied_by, applied_by_name, applied_at "
            + "FROM engine.model_version ORDER BY model, version DESC");
    models.forEach(model -> model.put("held", registry.isHeld((String) model.get("model"))));
    // A model whose first apply stopped has no version yet, but its tables may exist.
    models.addAll(
        jdbc.queryForList("SELECT r.model, 0 AS version, NULL AS hash, r.actor AS applied_by, "
            + "NULL AS applied_by_name, r.started_at AS applied_at, TRUE AS held "
            + "FROM engine.apply_run r WHERE r.id = (SELECT max(id) FROM engine.apply_run "
            + "WHERE model = r.model) AND r.status IN ('running', 'failed') AND NOT EXISTS "
            + "(SELECT 1 FROM engine.model_version v WHERE v.model = r.model)"));
    models.sort(Comparator.comparing(model -> (String) model.get("model")));
    return models;
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
  private static List<String> unexpectedDrops(Set<String> allowed, SchemaTool.Plan plan) {
    return plan.statements().stream()
        .filter(statement -> statement.dropsData() && !allowed.contains(statement.path()))
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
      List<String> unexpected = unexpectedDrops(diff.allowedDrops, plan);
      if (!unexpected.isEmpty()) {
        throw new InvalidStateException(String.join("; ", unexpected));
      }
      schemaTool.apply(model, plan);
    }));
    diff.changes.stream().filter(change -> "DROP_ENTITY".equals(change.kind()))
        .forEach(change -> steps.add(new Step(new PlannedStep("DROP_FILES", change.entity()),
            () -> attachments.purgeEntity(model.name(), change.entity()))));
    String client = ModelClient.of(model.name());
    steps.add(
        new Step(new PlannedStep("ENSURE_CLIENT", client), () -> keycloak.ensureClient(client)));
    for (Role role : model.roles()) {
      steps.add(new Step(new PlannedStep("ENSURE_ROLE", role.name()),
          () -> keycloak.ensureClientRole(client, role.name(), role.description())));
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

  // The text as a LIKE pattern that matches only itself: names may hold "_", which LIKE reads as
  // any one character.
  static String likeLiteral(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
