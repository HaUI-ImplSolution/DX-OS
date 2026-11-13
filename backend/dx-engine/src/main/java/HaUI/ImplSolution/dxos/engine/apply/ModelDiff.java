// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import HaUI.ImplSolution.dxos.model.Model.Process;
import HaUI.ImplSolution.dxos.model.ProcessFamily;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// What changes between the running version of a model and a new one, and how risky it is. The
// names of fields only say what happened through renamed_from: a dropped field and a new one look
// the same as a rename, so that case stays a loss of data and asks the question.
public final class ModelDiff {

  public enum Level {
    SAFE,
    MIGRATION,
    LOSS
  }

  public record Change(Level level, String kind, String entity, String field, String detail) {}

  // Existing rows must satisfy this new rule before the change can run.
  public record RowCheck(
      String entity, String field, String column, Rule rule, List<String> options) {}

  public enum Rule {
    REQUIRED,
    REQUIRED_WHEN,
    UNIQUE,
    OPTIONS,
    ELEMENTS,
    MIN,
    MAX,
    PATTERN,
    NEW_REQUIRED
  }

  public final List<Change> changes = new ArrayList<>();
  public final List<String> rejections = new ArrayList<>();
  public final List<String> questions = new ArrayList<>();
  public final List<RowCheck> checks = new ArrayList<>();
  // Entity name to new field name to the column name it has now.
  public final Map<String, Map<String, String>> renames = new LinkedHashMap<>();
  // Entities whose process holds records that may be affected.
  public final Set<String> touchedProcesses = new LinkedHashSet<>();
  // The tables and columns the schema tool may remove, as its plan names them.
  public final Set<String> allowedDrops = new LinkedHashSet<>();

  private ModelDiff() {}

  // renamesOverride gives the renames from the outside, for a return to an earlier version.
  public static ModelDiff between(
      Model running, Model target, Map<String, Map<String, String>> renamesOverride) {
    ModelDiff diff = new ModelDiff();
    Map<String, Entity> before = running == null ? Map.of() : running.entities();
    Set<String> roles = new LinkedHashSet<>();
    if (running != null) {
      running.roles().forEach(role -> roles.add(role.name()));
    }
    target.roles().stream()
        .filter(role -> !roles.contains(role.name()))
        .forEach(
            role -> diff.changes.add(new Change(Level.SAFE, "ADD_ROLE", null, null, role.name())));
    Set<String> units = new LinkedHashSet<>();
    if (running != null) {
      running.units().forEach(unit -> units.add(unit.name()));
    }
    target.units().stream()
        .filter(unit -> !units.contains(unit.name()))
        .forEach(
            unit -> diff.changes.add(new Change(Level.SAFE, "ADD_UNIT", null, null, unit.name())));
    for (Entity entity : target.entities().values()) {
      Entity old = before.get(entity.name());
      if (old == null) {
        diff.changes.add(new Change(Level.SAFE, "ADD_ENTITY", entity.name(), null, null));
      } else {
        diff.compare(target, old, entity, renamesOverride.getOrDefault(entity.name(), Map.of()));
      }
    }
    for (Entity old : before.values()) {
      if (!target.entities().containsKey(old.name())) {
        diff.changes.add(
            new Change(
                Level.LOSS,
                "DROP_ENTITY",
                old.name(),
                null,
                "An entity cannot be renamed; its table, rows and attached files are removed"));
        diff.allowedDrops.add(target.schema() + "." + old.name());
        diff.touchedProcesses.add(old.name());
      }
    }
    return diff;
  }

  public Level level() {
    return changes.stream().map(Change::level).max(Comparator.naturalOrder()).orElse(Level.SAFE);
  }

  private void compare(Model target, Entity old, Entity entity, Map<String, String> override) {
    Map<String, String> renamed = new LinkedHashMap<>();
    for (Field field : entity.fields().values()) {
      String from = override.get(field.name());
      if (from == null
          && !old.fields().containsKey(field.name())
          && field.renamedFrom() != null
          && old.fields().containsKey(field.renamedFrom())) {
        from = field.renamedFrom();
      }
      if (from != null) {
        renamed.put(field.name(), from);
      }
    }
    if (!renamed.isEmpty()) {
      renames.put(entity.name(), renamed);
    }
    Set<String> kept = new LinkedHashSet<>(renamed.values());
    List<Field> added = new ArrayList<>();
    for (Field field : entity.fields().values()) {
      String from = renamed.get(field.name());
      Field before = old.fields().get(from != null ? from : field.name());
      if (before == null) {
        added.add(field);
        addField(entity, field);
        continue;
      }
      kept.add(before.name());
      if (from != null) {
        changes.add(
            new Change(
                Level.MIGRATION,
                "RENAME_FIELD",
                entity.name(),
                field.name(),
                "Renamed from " + from + "; rows are kept, callers of the old name break"));
      }
      changeField(entity, before, field);
    }
    List<Field> dropped =
        old.fields().values().stream().filter(field -> !kept.contains(field.name())).toList();
    for (Field field : dropped) {
      changes.add(
          new Change(
              Level.LOSS,
              "DROP_FIELD",
              entity.name(),
              field.name(),
              "The column and its values are removed"));
      allowedDrops.add(target.schema() + "." + entity.name() + "." + field.name());
      for (Field candidate : added) {
        if (candidate.type() == field.type()) {
          questions.add(
              "Is "
                  + entity.name()
                  + "."
                  + candidate.name()
                  + " a rename of "
                  + field.name()
                  + "? Then declare renamed_from: "
                  + field.name()
                  + " to keep the values");
        }
      }
    }
    compareRest(old, entity);
    if (old.process() != null
        && changes.stream().anyMatch(change -> entity.name().equals(change.entity()))) {
      touchedProcesses.add(entity.name());
    }
  }

  private void addField(Entity entity, Field field) {
    boolean code = field.type() == FieldType.CODE;
    Level level = field.required() || code ? Level.MIGRATION : Level.SAFE;
    changes.add(
        new Change(
            level,
            "ADD_FIELD",
            entity.name(),
            field.name(),
            code ? "Existing records receive a code" : null));
    if (field.required() && field.defaultValue() == null) {
      checks.add(new RowCheck(entity.name(), field.name(), null, Rule.NEW_REQUIRED, List.of()));
    }
    if (field.requiredWhen() != null) {
      checks.add(new RowCheck(entity.name(), field.name(), null, Rule.REQUIRED_WHEN, List.of()));
    }
  }

  private void changeField(Entity entity, Field before, Field field) {
    String name = field.name();
    if (before.type() != field.type()) {
      rejections.add(
          "Field "
              + entity.name()
              + "."
              + name
              + " changes its type from "
              + before.type().key()
              + " to "
              + field.type().key()
              + ", which is not supported; add a new field instead");
      return;
    }
    if (before.multiple() != field.multiple()) {
      rejections.add(
          "Field "
              + entity.name()
              + "."
              + name
              + " changes between one value and"
              + " several, which is not supported; add a new field instead");
      return;
    }
    if (field.type() == FieldType.REFERENCE && !Objects.equals(before.to(), field.to())) {
      rejections.add(
          "Field "
              + entity.name()
              + "."
              + name
              + " refers to another entity, which is"
              + " not supported; add a new field instead");
    }
    String column = before.name();
    if (field.type() == FieldType.CODE && !Objects.equals(before.format(), field.format())) {
      changes.add(new Change(Level.SAFE, "CHANGE_FORMAT", entity.name(), name, field.format()));
    }
    if (field.required() && !before.required()) {
      changes.add(new Change(Level.MIGRATION, "ADD_REQUIRED", entity.name(), name, null));
      checks.add(new RowCheck(entity.name(), name, column, Rule.REQUIRED, List.of()));
    } else if (before.required() && !field.required()) {
      changes.add(new Change(Level.SAFE, "DROP_REQUIRED", entity.name(), name, null));
    }
    if (field.unique() && !before.unique()) {
      changes.add(new Change(Level.MIGRATION, "ADD_UNIQUE", entity.name(), name, null));
      checks.add(new RowCheck(entity.name(), name, column, Rule.UNIQUE, List.of()));
    } else if (before.unique() && !field.unique()) {
      changes.add(new Change(Level.SAFE, "DROP_UNIQUE", entity.name(), name, null));
    }
    if (!before.options().equals(field.options())) {
      boolean narrower = !field.options().containsAll(before.options());
      changes.add(
          new Change(
              narrower ? Level.MIGRATION : Level.SAFE,
              "CHANGE_OPTIONS",
              entity.name(),
              name,
              String.join(", ", field.options())));
      if (narrower) {
        checks.add(
            new RowCheck(
                entity.name(),
                name,
                column,
                field.multiple() ? Rule.ELEMENTS : Rule.OPTIONS,
                field.options()));
      }
    }
    compareBound(entity, before, field, column, true);
    compareBound(entity, before, field, column, false);
    if (!Objects.equals(before.pattern(), field.pattern())) {
      boolean added = field.pattern() != null;
      changes.add(
          new Change(
              added ? Level.MIGRATION : Level.SAFE,
              added ? "CHANGE_PATTERN" : "DROP_PATTERN",
              entity.name(),
              name,
              field.pattern()));
      if (added) {
        checks.add(
            new RowCheck(entity.name(), name, column, Rule.PATTERN, List.of(field.pattern())));
      }
    }
    if (!Objects.equals(before.requiredWhen(), field.requiredWhen())) {
      boolean added = field.requiredWhen() != null;
      changes.add(
          new Change(
              added ? Level.MIGRATION : Level.SAFE,
              added ? "CHANGE_REQUIRED_WHEN" : "DROP_REQUIRED_WHEN",
              entity.name(),
              name,
              null));
      if (added) {
        checks.add(new RowCheck(entity.name(), name, column, Rule.REQUIRED_WHEN, List.of()));
      }
    }
    if (!Objects.equals(before.label(), field.label())) {
      changes.add(new Change(Level.SAFE, "CHANGE_LABEL", entity.name(), name, field.label()));
    }
    if (!Objects.equals(before.defaultValue(), field.defaultValue())) {
      changes.add(new Change(Level.SAFE, "CHANGE_DEFAULT", entity.name(), name, null));
    }
  }

  // A lower or upper bound: lifting it applies at once, adding or tightening it needs the rows to
  // fit.
  private void compareBound(
      Entity entity, Field before, Field field, String column, boolean lower) {
    String was = lower ? before.min() : before.max();
    String now = lower ? field.min() : field.max();
    if (Objects.equals(was, now)) {
      return;
    }
    String kind = (now == null ? "DROP_" : "CHANGE_") + (lower ? "MIN" : "MAX");
    boolean tighter = now != null && (was == null || stricter(field, was, now, lower));
    changes.add(
        new Change(tighter ? Level.MIGRATION : Level.SAFE, kind, entity.name(), field.name(), now));
    if (tighter) {
      checks.add(
          new RowCheck(
              entity.name(), field.name(), column, lower ? Rule.MIN : Rule.MAX, List.of(now)));
    }
  }

  private static boolean stricter(Field field, String was, String now, boolean lower) {
    int order =
        field.type() == FieldType.DATE
            ? java.time.LocalDate.parse(now).compareTo(java.time.LocalDate.parse(was))
            : new java.math.BigDecimal(now).compareTo(new java.math.BigDecimal(was));
    return lower ? order > 0 : order < 0;
  }

  private void compareRest(Entity old, Entity entity) {
    if (!old.access().equals(entity.access())) {
      changes.add(new Change(Level.SAFE, "CHANGE_ACCESS", entity.name(), null, null));
    }
    if (!Objects.equals(old.ownerVia(), entity.ownerVia())) {
      changes.add(new Change(Level.SAFE, "CHANGE_OWNER_VIA", entity.name(), null, null));
    }
    if (!Objects.equals(old.label(), entity.label())) {
      changes.add(new Change(Level.SAFE, "CHANGE_LABEL", entity.name(), null, entity.label()));
    }
    if (!Objects.equals(old.ui(), entity.ui())) {
      changes.add(new Change(Level.SAFE, "CHANGE_UI", entity.name(), null, null));
    }
    Process before = old.process();
    Process after = entity.process();
    if (before == null && after == null) {
      return;
    }
    if (before == null || after == null) {
      checks.add(new RowCheck(entity.name(), null, null, Rule.REQUIRED, List.of()));
      changes.add(
          new Change(
              Level.MIGRATION,
              after == null ? "DROP_PROCESS" : "ADD_PROCESS",
              entity.name(),
              null,
              "Only for an entity without rows"));
      return;
    }
    if (before.family() != null
        && after.family() != null
        && !before.family().equals(after.family())) {
      rejections.add(
          "Entity "
              + entity.name()
              + " changes its process family, which is not"
              + " supported yet");
    }
    if (!before.handlers().equals(after.handlers())) {
      changes.add(
          new Change(
              Level.SAFE,
              "CHANGE_HANDLER",
              entity.name(),
              null,
              String.join(", ", after.handlers())));
    }
    compareFlow(entity, before, after);
    if (before.allowSelfDecision() != after.allowSelfDecision()) {
      changes.add(
          new Change(
              Level.SAFE,
              "CHANGE_SELF_DECISION",
              entity.name(),
              null,
              String.valueOf(after.allowSelfDecision())));
    }
    if (!Objects.equals(before.after(), after.after())) {
      changes.add(
          new Change(
              Level.SAFE,
              "CHANGE_COMPLETION",
              entity.name(),
              null,
              String.join(", ", after.after().keySet())));
    }
    if (!Objects.equals(before.booking(), after.booking())) {
      changes.add(new Change(Level.SAFE, "CHANGE_BOOKING", entity.name(), null, null));
    }
    if (!Objects.equals(before.deadline(), after.deadline())) {
      changes.add(
          new Change(
              Level.SAFE,
              "CHANGE_DEADLINE",
              entity.name(),
              null,
              after.deadline() == null ? "none" : after.deadline().toString()));
    }
    if (!Objects.equals(before.escalations(), after.escalations())) {
      changes.add(
          new Change(
              Level.SAFE,
              "CHANGE_ESCALATION",
              entity.name(),
              null,
              after.escalations().isEmpty() ? "none" : String.valueOf(after.escalations().size())));
    }
    if (!Objects.equals(before.autoClose(), after.autoClose())
        || !Objects.equals(before.autoCloseAction(), after.autoCloseAction())) {
      changes.add(
          new Change(
              Level.SAFE,
              "CHANGE_AUTO_CLOSE",
              entity.name(),
              null,
              after.autoClose() == null ? "none" : after.autoClose().toString()));
    }
  }

  // The states and actions of a process. A change of the steps cannot reach records that are
  // still moving through it, so it is refused while there are any; labels, the roles of an action
  // and the fields it requires apply at once. A record that is finished stays finished, so a final
  // state that stops being final cannot hold rows: their process has ended and no action could
  // move them again.
  private void compareFlow(Entity entity, Process before, Process after) {
    ProcessFamily was = before.definition();
    ProcessFamily now = after.definition();
    if (!was.sameFlowAs(now)) {
      changes.add(
          new Change(Level.SAFE, "CHANGE_FLOW", entity.name(), null, flowSummary(was, now)));
      Set<String> reopened =
          was.states().stream()
              .filter(ProcessFamily.State::terminal)
              .map(ProcessFamily.State::name)
              .collect(Collectors.toCollection(LinkedHashSet::new));
      now.states().stream()
          .filter(ProcessFamily.State::terminal)
          .forEach(state -> reopened.remove(state.name()));
      checks.add(
          new RowCheck(
              entity.name(),
              "status",
              "status",
              Rule.OPTIONS,
              now.states().stream()
                  .map(ProcessFamily.State::name)
                  .filter(name -> !reopened.contains(name))
                  .toList()));
    } else if (!was.equals(now)) {
      changes.add(new Change(Level.SAFE, "CHANGE_ACTIONS", entity.name(), null, null));
    }
  }

  private static String flowSummary(ProcessFamily was, ProcessFamily now) {
    List<String> parts = new ArrayList<>();
    Set<String> oldStates =
        was.states().stream()
            .map(ProcessFamily.State::name)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<String> newStates =
        now.states().stream()
            .map(ProcessFamily.State::name)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<String> oldActions =
        was.actions().stream()
            .map(ProcessFamily.Action::name)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<String> newActions =
        now.actions().stream()
            .map(ProcessFamily.Action::name)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    addDelta(parts, "states", oldStates, newStates);
    addDelta(parts, "actions", oldActions, newActions);
    return parts.isEmpty() ? "steps of existing actions" : String.join("; ", parts);
  }

  private static void addDelta(
      List<String> parts, String what, Set<String> before, Set<String> after) {
    List<String> added = after.stream().filter(name -> !before.contains(name)).toList();
    List<String> removed = before.stream().filter(name -> !after.contains(name)).toList();
    if (!added.isEmpty()) {
      parts.add(what + " added: " + String.join(", ", added));
    }
    if (!removed.isEmpty()) {
      parts.add(what + " removed: " + String.join(", ", removed));
    }
  }
}
