// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// A parsed and validated model file.
// webhook names the target the history of the records goes to, or is null for the default target.
public record Model(
    int format,
    String name,
    String webhook,
    List<Role> roles,
    List<Unit> units,
    Map<String, Entity> entities) {

  public record Role(String name, String description, boolean staff) {}

  // An organisational unit; its members are the Keycloak group of the same name.
  public record Unit(String name, String label) {}

  // ui is how the entity looks on screen, or null when the model file has no ui block.
  public record Entity(
      String name,
      String label,
      Map<String, Field> fields,
      Map<String, Scope> access,
      Process process,
      String ownerVia,
      Ui ui) {

    // An entity without a ui block.
    public Entity(
        String name,
        String label,
        Map<String, Field> fields,
        Map<String, Scope> access,
        Process process,
        String ownerVia) {
      this(name, label, fields, access, process, ownerVia, null);
    }

    public Optional<Field> field(String fieldName) {
      return Optional.ofNullable(fields.get(fieldName));
    }

    // The field that names the unit a record belongs to.
    public Optional<Field> unitField() {
      return fields.values().stream().filter(f -> f.type() == FieldType.UNIT).findFirst();
    }
  }

  // renamedFrom names the column this field had in the version before; defaultValue fills existing
  // and new rows. multiple lets a choice field hold several options. min and max bound a number or
  // date, as canonical text; pattern bounds a text and matches the whole value. requiredWhen makes
  // the field required only for the records that meet the condition.
  public record Field(
      String name,
      FieldType type,
      String label,
      boolean required,
      boolean unique,
      List<String> options,
      String to,
      String renamedFrom,
      String defaultValue,
      boolean multiple,
      String min,
      String max,
      String pattern,
      ProcessFamily.Condition requiredWhen) {

    // A field without multiple, bounds, pattern or condition.
    public Field(
        String name,
        FieldType type,
        String label,
        boolean required,
        boolean unique,
        List<String> options,
        String to,
        String renamedFrom,
        String defaultValue) {
      this(
          name,
          type,
          label,
          required,
          unique,
          options,
          to,
          renamedFrom,
          defaultValue,
          false,
          null,
          null,
          null,
          null);
    }
  }

  // How an entity looks on screen: an icon from ICONS, the columns and sort of the list (a field or
  // one of LIST_COLUMNS, a leading minus sorts descending), the groups of the form (a field in no
  // group follows them) and a line of help per field.
  public record Ui(
      String icon, Menu menu, ListView list, FormView form, Map<String, String> hints) {

    public static final List<String> ICONS =
        List.of(
            "description",
            "folder",
            "inbox",
            "person",
            "group",
            "school",
            "assignment",
            "event",
            "room",
            "computer",
            "build",
            "inventory",
            "payments",
            "support",
            "shield",
            "settings",
            "list",
            "calendar",
            "chat",
            "local_shipping");

    // The columns every record has, beside the fields of its entity.
    public static final List<String> LIST_COLUMNS =
        List.of("status", "assignee", "created_at", "updated_at");

    public record Menu(String group, int order) {}

    public record ListView(List<String> columns, String sort) {}

    public record FormView(List<FormGroup> groups) {}

    public record FormGroup(String label, List<String> fields) {}
  }

  // The process an entity runs: the family it started from (null when it declares every state and
  // action itself), the roles that handle its records, whether the creator of a record may handle
  // it,
  // and how long a handler has before a record counts as overdue (null for no deadline). AutoClose
  // is
  // how long a record waits for its owner before the system takes autoCloseAction for them (both
  // null for never). After maps a final state to the actions taken when a record reaches it.
  // Booking
  // is set for the reservation family only. Definition is the states and actions the entity ends up
  // with after the declarations of the model are applied to the family.
  public record Process(
      String family,
      List<String> handlers,
      boolean allowSelfDecision,
      Duration deadline,
      Duration autoClose,
      String autoCloseAction,
      Map<String, List<Completion>> after,
      Booking booking,
      ProcessFamily definition) {

    // Whether the steps differ from those of the family, so the entity needs a BPMN file of its
    // own.
    public boolean ownFlow() {
      return family == null
          || !ProcessFamily.find(family)
              .map(template -> template.sameFlowAs(definition))
              .orElse(false);
    }

    // The key of the BPMN process the records of the entity run.
    public String processKey(String modelName, String entityName) {
      return ownFlow() ? modelName + "." + entityName : family;
    }

    // The roles that may take an action of the handler.
    public List<String> rolesFor(ProcessFamily.Action action) {
      return action.roles().isEmpty() ? handlers : action.roles();
    }

    // The roles that may take a handler action on a record in the state.
    public java.util.Set<String> rolesIn(String state) {
      java.util.Set<String> roles = new java.util.LinkedHashSet<>();
      definition.actions().stream()
          .filter(action -> action.by() == ProcessFamily.Actor.HANDLER && action.allowedIn(state))
          .forEach(action -> roles.addAll(rolesFor(action)));
      return roles;
    }

    // Every role that takes part in handling.
    public java.util.Set<String> allRoles() {
      java.util.Set<String> roles = new java.util.LinkedHashSet<>(handlers);
      definition.actions().forEach(action -> roles.addAll(action.roles()));
      return roles;
    }
  }

  // Name of the PostgreSQL schema that holds the tables of this model.
  public String schema() {
    return "m_" + name;
  }

  public Optional<Entity> entity(String entityName) {
    return Optional.ofNullable(entities.get(entityName));
  }
}
