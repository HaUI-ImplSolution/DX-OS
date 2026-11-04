// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import HaUI.ImplSolution.dxos.model.Model.Process;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// What changes between the running version of a model and a new one, and how risky it is. The
// names of fields only say what happened through renamed_from: a dropped field and a new one look
// the same as a rename, so that case stays a loss of data and asks the question.
public final class ModelDiff {

  public enum Level {
    SAFE, MIGRATION, LOSS
  }

  public record Change(Level level, String kind, String entity, String field, String detail) {}

  // Existing rows must satisfy this new rule before the change can run.
  public record RowCheck(String entity, String field, String column, Rule rule,
      List<String> options) {}

  public enum Rule {
    REQUIRED, UNIQUE, OPTIONS, NEW_REQUIRED
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
  public static ModelDiff between(Model running, Model target,
      Map<String, Map<String, String>> renamesOverride) {
    ModelDiff diff = new ModelDiff();
    Map<String, Entity> before = running == null ? Map.of() : running.entities();
    Set<String> roles = new LinkedHashSet<>();
    if (running != null) {
      running.roles().forEach(role -> roles.add(role.name()));
    }
    target.roles().stream().filter(role -> !roles.contains(role.name())).forEach(
        role -> diff.changes.add(new Change(Level.SAFE, "ADD_ROLE", null, null, role.name())));
    Set<String> units = new LinkedHashSet<>();
    if (running != null) {
      running.units().forEach(unit -> units.add(unit.name()));
    }
    target.units().stream().filter(unit -> !units.contains(unit.name())).forEach(
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
        diff.changes.add(new Change(Level.LOSS, "DROP_ENTITY", old.name(), null,
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
      if (from == null && !old.fields().containsKey(field.name()) && field.renamedFrom() != null
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
        changes.add(new Change(Level.MIGRATION, "RENAME_FIELD", entity.name(), field.name(),
            "Renamed from " + from + "; rows are kept, callers of the old name break"));
      }
      changeField(entity, before, field);
    }
    List<Field> dropped =
        old.fields().values().stream().filter(field -> !kept.contains(field.name())).toList();
    for (Field field : dropped) {
      changes.add(new Change(Level.LOSS, "DROP_FIELD", entity.name(), field.name(),
          "The column and its values are removed"));
      allowedDrops.add(target.schema() + "." + entity.name() + "." + field.name());
      for (Field candidate : added) {
        if (candidate.type() == field.type()) {
          questions
              .add("Is " + entity.name() + "." + candidate.name() + " a rename of " + field.name()
                  + "? Then declare renamed_from: " + field.name() + " to keep the values");
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
    Level level = field.required() ? Level.MIGRATION : Level.SAFE;
    changes.add(new Change(level, "ADD_FIELD", entity.name(), field.name(), null));
    if (field.required() && field.defaultValue() == null) {
      checks.add(new RowCheck(entity.name(), field.name(), null, Rule.NEW_REQUIRED, List.of()));
    }
  }

  private void changeField(Entity entity, Field before, Field field) {
    String name = field.name();
    if (before.type() != field.type()) {
      rejections.add(
          "Field " + entity.name() + "." + name + " changes its type from " + before.type().key()
              + " to " + field.type().key() + ", which is not supported; add a new field instead");
      return;
    }
    if (field.type() == FieldType.REFERENCE && !Objects.equals(before.to(), field.to())) {
      rejections.add("Field " + entity.name() + "." + name + " refers to another entity, which is"
          + " not supported; add a new field instead");
    }
    String column = before.name();
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
      changes.add(new Change(narrower ? Level.MIGRATION : Level.SAFE, "CHANGE_OPTIONS",
          entity.name(), name, String.join(", ", field.options())));
      if (narrower) {
        checks.add(new RowCheck(entity.name(), name, column, Rule.OPTIONS, field.options()));
      }
    }
    if (!Objects.equals(before.label(), field.label())) {
      changes.add(new Change(Level.SAFE, "CHANGE_LABEL", entity.name(), name, field.label()));
    }
    if (!Objects.equals(before.defaultValue(), field.defaultValue())) {
      changes.add(new Change(Level.SAFE, "CHANGE_DEFAULT", entity.name(), name, null));
    }
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
    Process before = old.process();
    Process after = entity.process();
    if (before == null && after == null) {
      return;
    }
    if (before == null || after == null) {
      checks.add(new RowCheck(entity.name(), null, null, Rule.REQUIRED, List.of()));
      changes.add(new Change(Level.MIGRATION, after == null ? "DROP_PROCESS" : "ADD_PROCESS",
          entity.name(), null, "Only for an entity without rows"));
      return;
    }
    if (!before.family().equals(after.family())) {
      rejections.add("Entity " + entity.name() + " changes its process family, which is not"
          + " supported yet");
    }
    if (!before.handler().equals(after.handler())) {
      changes.add(new Change(Level.SAFE, "CHANGE_HANDLER", entity.name(), null, after.handler()));
    }
    if (before.allowSelfDecision() != after.allowSelfDecision()) {
      changes.add(new Change(Level.SAFE, "CHANGE_SELF_DECISION", entity.name(), null,
          String.valueOf(after.allowSelfDecision())));
    }
    if (!Objects.equals(before.after(), after.after())) {
      changes.add(new Change(Level.SAFE, "CHANGE_COMPLETION", entity.name(), null,
          String.join(", ", after.after().keySet())));
    }
    if (!Objects.equals(before.booking(), after.booking())) {
      changes.add(new Change(Level.SAFE, "CHANGE_BOOKING", entity.name(), null, null));
    }
    if (!Objects.equals(before.deadline(), after.deadline())) {
      changes.add(new Change(Level.SAFE, "CHANGE_DEADLINE", entity.name(), null,
          after.deadline() == null ? "none" : after.deadline().toString()));
    }
  }
}
