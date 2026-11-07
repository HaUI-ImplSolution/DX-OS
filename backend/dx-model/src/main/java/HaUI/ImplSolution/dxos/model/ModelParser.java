// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import HaUI.ImplSolution.dxos.model.Model.Process;
import HaUI.ImplSolution.dxos.model.Model.Role;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Action;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Actor;
import HaUI.ImplSolution.dxos.model.ProcessFamily.State;
import HaUI.ImplSolution.dxos.model.Model.Unit;
import HaUI.ImplSolution.dxos.model.ModelException.Problem;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

// Reads a model file and checks it. Every problem carries its line and column; nothing outside the
// file is touched.
public final class ModelParser {

  public static final int FORMAT = 0;

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,39}");
  private static final Pattern DEADLINE = Pattern.compile("([0-9]{1,6})([mhd])");
  private static final Pattern ROLE = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");
  // Roles the platform owns: a model that declared one would hand platform rights to its users.
  private static final Set<String> RESERVED_ROLES = Set.of("ADMIN");
  private static final Set<String> RESERVED_FIELDS =
      Set.of("id", "created_by", "created_at", "updated_at", "version", "status", "assignee",
          "decided_by", "decided_at", "handle_by", "overdue");

  private final List<Problem> problems = new ArrayList<>();

  private final Set<String> unitNames = new LinkedHashSet<>();
  private final Map<String, Node> accessNodes = new LinkedHashMap<>();

  private ModelParser() {}

  public static Model parse(String yaml) {
    ModelParser parser = new ModelParser();
    Model model = parser.read(yaml);
    if (!parser.problems.isEmpty()) {
      throw new ModelException(parser.problems);
    }
    return model;
  }

  private Model read(String yaml) {
    Node root;
    try {
      LoaderOptions options = new LoaderOptions();
      options.setMaxAliasesForCollections(0);
      options.setAllowDuplicateKeys(false);
      root = new Yaml(options).compose(new StringReader(yaml));
    } catch (MarkedYAMLException e) {
      int line = e.getProblemMark() == null ? 0 : e.getProblemMark().getLine() + 1;
      int column = e.getProblemMark() == null ? 0 : e.getProblemMark().getColumn() + 1;
      problems.add(new Problem(line, column, "invalid YAML: " + e.getProblem()));
      return null;
    }
    if (!(root instanceof MappingNode top)) {
      problem(root, "the file must be a mapping with format, name, roles and entities");
      return null;
    }
    Map<String, Node> keys =
        mapping(top, Set.of("format", "name", "webhook", "roles", "units", "entities"));

    int format = readFormat(top, keys.get("format"));
    String name = readName(top, keys.get("name"), "model name");
    String webhook =
        keys.get("webhook") == null ? null : readName(top, keys.get("webhook"), "webhook");
    List<Role> roles = readRoles(top, keys.get("roles"));
    Set<String> roleNames = new LinkedHashSet<>();
    roles.forEach(role -> roleNames.add(role.name()));
    List<Unit> units = readUnits(keys.get("units"));
    units.forEach(unit -> unitNames.add(unit.name()));

    Map<String, Entity> entities = new LinkedHashMap<>();
    Node entitiesNode = keys.get("entities");
    if (entitiesNode == null) {
      problem(top, "entities is required");
    } else if (!(entitiesNode instanceof MappingNode entityMap) || entityMap.getValue().isEmpty()) {
      problem(entitiesNode, "entities must be a mapping with at least one entity");
    } else {
      Set<String> entityNames = new LinkedHashSet<>();
      for (NodeTuple tuple : entityMap.getValue()) {
        entityNames.add(scalar(tuple.getKeyNode()));
      }
      for (NodeTuple tuple : entityMap.getValue()) {
        Entity entity = readEntity(tuple, roleNames, entityNames);
        if (entity != null) {
          entities.put(entity.name(), entity);
        }
      }
    }
    checkScopes(entities);
    if (!problems.isEmpty()) {
      return null;
    }
    return new Model(format, name, webhook, List.copyOf(roles), List.copyOf(units), entities);
  }

  private List<Unit> readUnits(Node node) {
    List<Unit> units = new ArrayList<>();
    if (node == null) {
      return units;
    }
    if (!(node instanceof SequenceNode sequence) || sequence.getValue().isEmpty()) {
      problem(node, "units must be a list with at least one unit");
      return units;
    }
    Set<String> seen = new LinkedHashSet<>();
    for (Node item : sequence.getValue()) {
      if (!(item instanceof MappingNode map)) {
        problem(item, "a unit is a mapping with name and label");
        continue;
      }
      Map<String, Node> keys = mapping(map, Set.of("name", "label"));
      Node nameNode = keys.get("name");
      String unitName = nameNode instanceof ScalarNode s ? s.getValue() : null;
      if (unitName == null || !ROLE.matcher(unitName).matches()) {
        problem(nameNode == null ? map : nameNode,
            "unit name must match [A-Z][A-Z0-9_]* and be at most 40 characters");
        continue;
      }
      if (!seen.add(unitName)) {
        problem(nameNode, "unit " + unitName + " is declared twice");
        continue;
      }
      Node label = keys.get("label");
      units.add(new Unit(unitName, label == null ? unitName : scalar(label)));
    }
    return units;
  }

  // A scope needs what it filters on, on the entity or on the record at the end of its owner_via
  // chain.
  private void checkScopes(Map<String, Entity> entities) {
    for (Entity entity : entities.values()) {
      Entity holder = holder(entities, entity);
      if (entity.ownerVia() != null && loops(entities, entity)) {
        problem(accessNodes.get(entity.name()),
            "owner_via of " + entity.name() + " leads back to " + entity.name());
        continue;
      }
      if (entity.fields().values().stream().filter(f -> f.type() == FieldType.UNIT).count() > 1) {
        problem(accessNodes.get(entity.name()),
            "entity " + entity.name() + " has more than one unit field");
      }
      if (holder == null) {
        continue;
      }
      for (Map.Entry<String, Scope> grant : entity.access().entrySet()) {
        if (grant.getValue() == Scope.UNIT && holder.unitField().isEmpty()) {
          problem(accessNodes.get(entity.name()),
              "access unit of " + grant.getKey() + " needs a unit field on " + holder.name());
        }
        if (grant.getValue() == Scope.ASSIGNED && holder.process() == null) {
          problem(accessNodes.get(entity.name()),
              "access assigned of " + grant.getKey() + " needs a process on " + holder.name());
        }
      }
    }
  }

  private static boolean loops(Map<String, Entity> entities, Entity entity) {
    Set<String> seen = new HashSet<>();
    Entity current = entity;
    while (current != null && current.ownerVia() != null) {
      if (!seen.add(current.name())) {
        return true;
      }
      Field via = current.fields().get(current.ownerVia());
      current = via == null ? null : entities.get(via.to());
    }
    return false;
  }

  // The entity whose own columns decide who reaches a record of this entity; null when the chain
  // is broken or loops back on itself.
  private static Entity holder(Map<String, Entity> entities, Entity entity) {
    Set<String> seen = new HashSet<>();
    Entity current = entity;
    while (current.ownerVia() != null) {
      if (!seen.add(current.name())) {
        return null;
      }
      Field via = current.fields().get(current.ownerVia());
      current = via == null ? null : entities.get(via.to());
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  private int readFormat(Node parent, Node node) {
    if (node == null) {
      problem(parent, "format is required");
      return -1;
    }
    if (!(node instanceof ScalarNode scalar) || !String.valueOf(FORMAT).equals(scalar.getValue())) {
      problem(node, "unsupported format, this version reads format " + FORMAT);
      return -1;
    }
    return FORMAT;
  }

  private String readName(Node parent, Node node, String what) {
    if (node == null) {
      problem(parent, what + " is required");
      return null;
    }
    String value = node instanceof ScalarNode scalar ? scalar.getValue() : null;
    if (value == null || !NAME.matcher(value).matches()) {
      problem(node, what + " must match [a-z][a-z0-9_]* and be at most 40 characters");
      return null;
    }
    return value;
  }

  private List<Role> readRoles(Node parent, Node node) {
    List<Role> roles = new ArrayList<>();
    if (node == null) {
      problem(parent, "roles is required");
      return roles;
    }
    if (!(node instanceof SequenceNode sequence) || sequence.getValue().isEmpty()) {
      problem(node, "roles must be a list with at least one role");
      return roles;
    }
    Set<String> seen = new LinkedHashSet<>();
    for (Node item : sequence.getValue()) {
      if (!(item instanceof MappingNode map)) {
        problem(item, "a role is a mapping with name, description and staff");
        continue;
      }
      Map<String, Node> keys = mapping(map, Set.of("name", "description", "staff"));
      Node nameNode = keys.get("name");
      String roleName = nameNode instanceof ScalarNode s ? s.getValue() : null;
      if (roleName == null || !ROLE.matcher(roleName).matches()) {
        problem(nameNode == null ? map : nameNode,
            "role name must match [A-Z][A-Z0-9_]* and be at most 40 characters");
        continue;
      }
      if (RESERVED_ROLES.contains(roleName)) {
        problem(nameNode, "role " + roleName + " is reserved for the platform");
        continue;
      }
      if (!seen.add(roleName)) {
        problem(nameNode, "role " + roleName + " is declared twice");
        continue;
      }
      Node staff = keys.get("staff");
      boolean isStaff = false;
      if (staff != null) {
        String value = scalar(staff);
        if (!"true".equals(value) && !"false".equals(value)) {
          problem(staff, "staff must be true or false");
        }
        isStaff = "true".equals(value);
      }
      Node description = keys.get("description");
      roles.add(new Role(roleName, description == null ? "" : scalar(description), isStaff));
    }
    return roles;
  }

  private Entity readEntity(NodeTuple tuple, Set<String> roleNames, Set<String> entityNames) {
    Node keyNode = tuple.getKeyNode();
    String name = scalar(keyNode);
    if (!NAME.matcher(name).matches()) {
      problem(keyNode, "entity name must match [a-z][a-z0-9_]* and be at most 40 characters");
      return null;
    }
    if (!(tuple.getValueNode() instanceof MappingNode map)) {
      problem(tuple.getValueNode(), "an entity is a mapping with fields and access");
      return null;
    }
    Map<String, Node> keys =
        mapping(map, Set.of("label", "fields", "access", "process", "owner_via"));

    Map<String, Field> fields = readFields(map, keys.get("fields"), entityNames);
    Map<String, Scope> access = readAccess(map, keys.get("access"), roleNames);
    accessNodes.put(name, keys.get("access"));
    Process process = readProcess(keys.get("process"), roleNames, access, fields);

    String ownerVia = null;
    Node ownerNode = keys.get("owner_via");
    if (ownerNode != null) {
      ownerVia = scalar(ownerNode);
      Field target = fields.get(ownerVia);
      if (target == null || target.type() != FieldType.REFERENCE) {
        problem(ownerNode, "owner_via must name a reference field of " + name);
      }
    }
    for (Field field : fields.values()) {
      if (field.renamedFrom() != null && fields.containsKey(field.renamedFrom())) {
        problem(keys.get("fields"), "field " + field.name() + " is renamed from "
            + field.renamedFrom() + ", which still exists");
      }
    }
    Node label = keys.get("label");
    return new Entity(name, label == null ? name : scalar(label), fields, access, process,
        ownerVia);
  }

  private Map<String, Field> readFields(Node parent, Node node, Set<String> entityNames) {
    Map<String, Field> fields = new LinkedHashMap<>();
    if (node == null) {
      problem(parent, "fields is required");
      return fields;
    }
    if (!(node instanceof MappingNode map) || map.getValue().isEmpty()) {
      problem(node, "fields must be a mapping with at least one field");
      return fields;
    }
    for (NodeTuple tuple : map.getValue()) {
      Node keyNode = tuple.getKeyNode();
      String name = scalar(keyNode);
      if (!NAME.matcher(name).matches()) {
        problem(keyNode, "field name must match [a-z][a-z0-9_]* and be at most 40 characters");
        continue;
      }
      if (RESERVED_FIELDS.contains(name)) {
        problem(keyNode, "field name " + name + " is reserved");
        continue;
      }
      if (!(tuple.getValueNode() instanceof MappingNode spec)) {
        problem(tuple.getValueNode(), "a field is a mapping with at least a type");
        continue;
      }
      Field field = readField(name, spec, entityNames);
      if (field != null) {
        fields.put(name, field);
      }
    }
    return fields;
  }

  private Field readField(String name, MappingNode spec, Set<String> entityNames) {
    Map<String, Node> keys = mapping(spec,
        Set.of("type", "label", "required", "unique", "options", "to", "renamed_from", "default"));
    Node typeNode = keys.get("type");
    if (typeNode == null) {
      problem(spec, "field " + name + " has no type");
      return null;
    }
    FieldType type = FieldType.of(scalar(typeNode)).orElse(null);
    if (type == null) {
      problem(typeNode, "unknown type " + scalar(typeNode)
          + ", use text, number, datetime, choice, reference, user, unit or file");
      return null;
    }
    List<String> options = List.of();
    if (type == FieldType.CHOICE) {
      options = readOptions(spec, keys.get("options"));
    } else if (keys.get("options") != null) {
      problem(keys.get("options"), "options only applies to a choice field");
    } else if (type == FieldType.UNIT) {
      options = List.copyOf(unitNames);
      if (options.isEmpty()) {
        problem(typeNode, "a unit field needs units declared in the model");
      }
    }
    String to = null;
    if (type == FieldType.REFERENCE) {
      Node toNode = keys.get("to");
      to = toNode == null ? null : scalar(toNode);
      if (to == null || !entityNames.contains(to)) {
        problem(toNode == null ? spec : toNode,
            "a reference field needs `to` naming an entity of this model");
      }
    } else if (keys.get("to") != null) {
      problem(keys.get("to"), "to only applies to a reference field");
    }
    boolean required = flag(keys.get("required"), "required");
    boolean unique = flag(keys.get("unique"), "unique");
    if (type == FieldType.FILE && (required || unique)) {
      problem(spec, "a file field is filled by uploading, so it cannot be required or unique");
    }
    String renamedFrom = readRenamedFrom(name, keys.get("renamed_from"));
    String defaultValue = readDefault(type, options, unique, keys.get("default"));
    Node label = keys.get("label");
    return new Field(name, type, label == null ? name : scalar(label), required, unique, options,
        to, renamedFrom, defaultValue);
  }

  private String readRenamedFrom(String name, Node node) {
    if (node == null) {
      return null;
    }
    String value = scalar(node);
    if (!NAME.matcher(value).matches() || value.equals(name)) {
      problem(node, "renamed_from must be the earlier name of field " + name);
      return null;
    }
    return value;
  }

  private String readDefault(FieldType type, List<String> options, boolean unique, Node node) {
    if (node == null) {
      return null;
    }
    String value = scalar(node);
    if (unique) {
      problem(node, "a unique field cannot have a default");
    } else if (type == FieldType.NUMBER) {
      try {
        new java.math.BigDecimal(value);
      } catch (NumberFormatException e) {
        problem(node, "the default of a number field must be a number");
      }
    } else if (type == FieldType.CHOICE || type == FieldType.UNIT) {
      if (!options.contains(value)) {
        problem(node, "the default must be one of the options");
      }
    } else if (type != FieldType.TEXT) {
      problem(node, "only text, number, choice and unit fields can have a default");
    }
    return value;
  }

  private List<String> readOptions(Node parent, Node node) {
    if (!(node instanceof SequenceNode sequence) || sequence.getValue().isEmpty()) {
      problem(node == null ? parent : node, "a choice field needs options, a non-empty list");
      return List.of();
    }
    Set<String> options = new LinkedHashSet<>();
    for (Node item : sequence.getValue()) {
      String value = scalar(item);
      if (value.isBlank() || value.length() > 60) {
        problem(item, "an option is text of 1 to 60 characters");
      } else if (!options.add(value)) {
        problem(item, "option " + value + " is listed twice");
      }
    }
    return List.copyOf(options);
  }

  private Map<String, Scope> readAccess(Node parent, Node node, Set<String> roleNames) {
    Map<String, Scope> access = new LinkedHashMap<>();
    if (node == null) {
      problem(parent, "access is required");
      return access;
    }
    if (!(node instanceof MappingNode map) || map.getValue().isEmpty()) {
      problem(node, "access maps at least one role to all, own, read, unit or assigned");
      return access;
    }
    for (NodeTuple tuple : map.getValue()) {
      String role = scalar(tuple.getKeyNode());
      if (!roleNames.contains(role)) {
        problem(tuple.getKeyNode(), "role " + role + " is not declared in roles");
        continue;
      }
      Scope scope = Scope.of(scalar(tuple.getValueNode()));
      if (scope == null) {
        problem(tuple.getValueNode(),
            "access of " + role + " must be all, own, read, unit or assigned");
        continue;
      }
      access.put(role, scope);
    }
    return access;
  }

  // An action while the declarations of a model are applied to a family, with the nodes it was
  // declared at so that a problem can point to them.
  private static final class ActionDraft {

    String label;
    List<String> from = new ArrayList<>();
    String to;
    Actor by = Actor.HANDLER;
    boolean needsNote;
    List<String> roles = List.of();
    List<String> require = List.of();
    Node node;
    Node fromNode;
    Node toNode;

    static ActionDraft of(Action action) {
      ActionDraft draft = new ActionDraft();
      draft.label = action.label();
      draft.from = new ArrayList<>(action.from());
      draft.to = action.to();
      draft.by = action.by();
      draft.needsNote = action.needsNote();
      draft.roles = action.roles();
      draft.require = action.require();
      return draft;
    }
  }

  private Process readProcess(Node node, Set<String> roleNames, Map<String, Scope> access,
      Map<String, Field> fields) {
    if (node == null) {
      return null;
    }
    if (!(node instanceof MappingNode map)) {
      problem(node, "process is a mapping with handler and a family or states and actions");
      return null;
    }
    Map<String, Node> keys = mapping(map, ProcessSyntax.PROCESS);
    Node familyNode = keys.get("family");
    String family = familyNode == null ? null : scalar(familyNode);
    ProcessFamily template = family == null ? null : ProcessFamily.find(family).orElse(null);
    if (family != null && template == null) {
      problem(familyNode,
          "unknown process family, available: " + String.join(", ", ProcessFamily.names()));
      return null;
    }
    List<String> handlers =
        readRoles(map, keys.get("handler"), "handler", "handler role", roleNames, access);
    if (handlers == null) {
      return null;
    }
    ProcessFamily definition = readDefinition(map, keys, template, roleNames, access, fields);
    if (definition == null) {
      return null;
    }
    AutoClose autoClose = readAutoClose(keys.get("auto_close"), family, definition);
    return new Process(family, handlers,
        flag(keys.get("allow_self_decision"), "allow_self_decision"),
        deadline(keys.get("deadline")), autoClose.after(), autoClose.action(),
        readAfter(keys.get("after"), definition, fields),
        readBooking(map, keys.get("booking"), family, fields), definition);
  }

  // One role or a list of roles, each declared in roles and able to read the records of the entity.
  private List<String> readRoles(Node parent, Node node, String key, String what,
      Set<String> roleNames, Map<String, Scope> access) {
    if (node == null) {
      problem(parent, key + " must be a role declared in roles");
      return null;
    }
    List<Node> items;
    if (node instanceof SequenceNode sequence) {
      items = sequence.getValue();
      if (items.isEmpty()) {
        problem(node, key + " needs at least one role");
        return null;
      }
    } else if (node instanceof ScalarNode text) {
      String value = text.getValue();
      if (value.contains(",") || value.trim().contains(" ")) {
        problem(node, key + " is one role or a list like [MANAGER, SELLER], got '" + value + "'");
        return null;
      }
      items = List.of(node);
    } else {
      problem(node, key + " is one role or a list of roles");
      return null;
    }
    List<String> roles = new ArrayList<>();
    for (Node item : items) {
      String role = scalar(item);
      if (!roleNames.contains(role)) {
        problem(item,
            node instanceof SequenceNode
                ? "role " + role + " in " + key + " is not declared in roles, declared: "
                    + String.join(", ", roleNames)
                : key + " must be a role declared in roles");
      } else if (roles.contains(role)) {
        problem(item, "role " + role + " is listed twice");
      } else if (access.get(role) == null || access.get(role) == Scope.OWN) {
        problem(item, "the " + what + " " + role + " must have access all, read, unit or assigned");
      } else {
        roles.add(role);
      }
    }
    return roles.size() == items.size() ? roles : null;
  }

  // The states and actions of the family with the declarations of the model applied, checked as a
  // whole. Null when something is wrong.
  private ProcessFamily readDefinition(MappingNode process, Map<String, Node> keys,
      ProcessFamily template, Set<String> roleNames, Map<String, Scope> access,
      Map<String, Field> fields) {
    int before = problems.size();
    Map<String, State> states = new LinkedHashMap<>();
    Map<String, ActionDraft> actions = new LinkedHashMap<>();
    Map<String, Node> nodes = new HashMap<>();
    String initial = null;
    if (template != null) {
      template.states().stream()
          .filter(state -> !ProcessFamily.WITHDRAWN_STATE.equals(state.name()))
          .forEach(state -> states.put(state.name(), state));
      template.actions().forEach(action -> actions.put(action.name(), ActionDraft.of(action)));
      initial = template.initial();
    }
    readStates(process, keys.get("states"), template != null, states, nodes);
    readActions(process, keys.get("actions"), template != null, actions, nodes, roleNames, access,
        fields);
    Node initialNode = keys.get("initial");
    if (initialNode != null) {
      initial = scalar(initialNode);
    } else if (template == null && !states.isEmpty()) {
      initial = states.keySet().iterator().next();
    }
    if (problems.size() > before) {
      return null;
    }
    checkDefinition(process, initialNode, initial, states, actions, nodes);
    if (problems.size() > before) {
      return null;
    }
    List<Action> built = new ArrayList<>();
    actions.forEach(
        (name, draft) -> built.add(new Action(name, draft.label, List.copyOf(draft.from), draft.to,
            draft.by, draft.needsNote, List.copyOf(draft.roles), List.copyOf(draft.require))));
    List<State> builtStates = new ArrayList<>(states.values());
    if (actions.containsKey(ProcessFamily.WITHDRAW)) {
      builtStates.add(ProcessFamily.WITHDRAWN);
    }
    return new ProcessFamily(template == null ? "custom" : template.name(), initial,
        List.copyOf(builtStates), List.copyOf(built));
  }

  private void readStates(MappingNode process, Node node, boolean fromFamily,
      Map<String, State> states, Map<String, Node> nodes) {
    if (node == null) {
      if (!fromFamily) {
        problem(process, "a process without a family needs states");
      }
      return;
    }
    if (!(node instanceof MappingNode map) || map.getValue().isEmpty()) {
      problem(node, "states is a mapping from a state name to its label and whether it is final");
      return;
    }
    for (NodeTuple tuple : map.getValue()) {
      Node keyNode = tuple.getKeyNode();
      String name = scalar(keyNode);
      if (!NAME.matcher(name).matches()) {
        problem(keyNode, "state name must match [a-z][a-z0-9_]* and be at most 40 characters");
        continue;
      }
      if (ProcessFamily.WITHDRAWN_STATE.equals(name)) {
        problem(keyNode, "state " + name + " is added by the platform for the action withdraw");
        continue;
      }
      if (isNull(tuple.getValueNode())) {
        if (states.remove(name) == null) {
          problem(keyNode, "there is no state " + name + " to remove");
        }
        continue;
      }
      if (!(tuple.getValueNode() instanceof MappingNode spec)) {
        problem(tuple.getValueNode(),
            "a state is a mapping with label and terminal, or null to remove it");
        continue;
      }
      Map<String, Node> parts = mapping(spec, ProcessSyntax.STATE);
      State existing = states.get(name);
      String label = parts.get("label") != null ? scalar(parts.get("label"))
          : existing != null ? existing.label() : name;
      boolean terminal = parts.get("terminal") != null ? flag(parts.get("terminal"), "terminal")
          : existing != null && existing.terminal();
      states.put(name, new State(name, label, terminal));
      nodes.put("state:" + name, keyNode);
    }
  }

  private void readActions(MappingNode process, Node node, boolean fromFamily,
      Map<String, ActionDraft> actions, Map<String, Node> nodes, Set<String> roleNames,
      Map<String, Scope> access, Map<String, Field> fields) {
    if (node == null) {
      if (!fromFamily) {
        problem(process, "a process without a family needs actions");
      }
      return;
    }
    if (!(node instanceof MappingNode map) || map.getValue().isEmpty()) {
      problem(node, "actions is a mapping from an action name to what it does");
      return;
    }
    for (NodeTuple tuple : map.getValue()) {
      Node keyNode = tuple.getKeyNode();
      String name = scalar(keyNode);
      if (!NAME.matcher(name).matches()) {
        problem(keyNode, "action name must match [a-z][a-z0-9_]* and be at most 40 characters");
        continue;
      }
      if (isNull(tuple.getValueNode())) {
        if (actions.remove(name) == null) {
          problem(keyNode, "there is no action " + name + " to remove");
        }
        continue;
      }
      if (!(tuple.getValueNode() instanceof MappingNode spec)) {
        problem(tuple.getValueNode(),
            "an action is a mapping with from and to, or null to remove it");
        continue;
      }
      ActionDraft draft = actions.get(name);
      boolean added = draft == null;
      if (added) {
        draft = new ActionDraft();
        draft.label = ProcessFamily.WITHDRAW.equals(name) ? ProcessFamily.WITHDRAW_LABEL : name;
      }
      draft.node = keyNode;
      if (readAction(name, spec, draft, added, roleNames, access, fields)) {
        actions.put(name, draft);
      }
    }
  }

  private boolean readAction(String name, MappingNode spec, ActionDraft draft, boolean added,
      Set<String> roleNames, Map<String, Scope> access, Map<String, Field> fields) {
    int before = problems.size();
    boolean withdraw = ProcessFamily.WITHDRAW.equals(name);
    Map<String, Node> parts = mapping(spec, ProcessSyntax.ACTION);
    if (parts.get("label") != null) {
      draft.label = scalar(parts.get("label"));
    }
    if (parts.get("from") != null) {
      draft.fromNode = parts.get("from");
      draft.from = readNames(draft.fromNode, "from");
    }
    if (withdraw) {
      if (parts.get("to") != null || parts.get("by") != null || parts.get("role") != null) {
        problem(spec,
            "withdraw is always taken by the owner and ends in " + ProcessFamily.WITHDRAWN_STATE);
      }
      draft.by = Actor.OWNER;
      draft.to = ProcessFamily.WITHDRAWN_STATE;
    } else {
      if (parts.get("to") != null) {
        draft.toNode = parts.get("to");
        draft.to = scalar(draft.toNode);
      }
      if (parts.get("by") != null) {
        String by = scalar(parts.get("by"));
        if ("handler".equals(by)) {
          draft.by = Actor.HANDLER;
        } else if ("owner".equals(by)) {
          draft.by = Actor.OWNER;
        } else {
          problem(parts.get("by"), "by is handler or owner");
        }
      }
      if (parts.get("role") != null) {
        if (draft.by != Actor.HANDLER) {
          problem(parts.get("role"), "role only applies to an action of the handler");
        } else {
          List<String> roles = readRoles(spec, parts.get("role"), "role", "role of action " + name,
              roleNames, access);
          draft.roles = roles == null ? List.of() : roles;
        }
      }
    }
    if (parts.get("needs_note") != null) {
      draft.needsNote = flag(parts.get("needs_note"), "needs_note");
    }
    if (parts.get("require") != null) {
      draft.require = readRequire(parts.get("require"), fields);
    }
    if (added && (draft.from.isEmpty() || (!withdraw && draft.to == null))) {
      problem(spec, "action " + name + " needs " + (withdraw ? "from" : "from and to"));
    }
    return problems.size() == before;
  }

  private List<String> readNames(Node node, String key) {
    List<Node> items = node instanceof SequenceNode sequence ? sequence.getValue() : List.of(node);
    List<String> names = new ArrayList<>();
    for (Node item : items) {
      String value = scalar(item);
      if (value.isBlank() || value.contains(",")) {
        problem(item, key + " is a state name or a list of state names");
      } else if (!names.contains(value)) {
        names.add(value);
      }
    }
    if (names.isEmpty()) {
      problem(node, key + " needs at least one state");
    }
    return names;
  }

  private List<String> readRequire(Node node, Map<String, Field> fields) {
    List<Node> items = node instanceof SequenceNode sequence ? sequence.getValue() : List.of(node);
    List<String> names = new ArrayList<>();
    for (Node item : items) {
      String value = scalar(item);
      Field field = fields.get(value);
      if (field == null || field.type() == FieldType.FILE) {
        problem(item,
            "require must name fields of this entity that are not files, fields: "
                + fields.values().stream().filter(f -> f.type() != FieldType.FILE).map(Field::name)
                    .collect(java.util.stream.Collectors.joining(", ")));
      } else if (!names.contains(value)) {
        names.add(value);
      }
    }
    return names;
  }

  // The rules that make a set of states and actions a process a record can run through.
  private void checkDefinition(MappingNode process, Node initialNode, String initial,
      Map<String, State> states, Map<String, ActionDraft> actions, Map<String, Node> nodes) {
    int before = problems.size();
    if (states.isEmpty()) {
      problem(process, "a process needs at least one state");
      return;
    }
    State start = states.get(initial);
    if (start == null) {
      problem(initialNode == null ? process : initialNode,
          "initial must be one of the states: " + String.join(", ", states.keySet()));
    } else if (start.terminal()) {
      problem(initialNode == null ? process : initialNode, "the initial state cannot be final");
    }
    if (states.values().stream().noneMatch(State::terminal)) {
      problem(process, "a process needs at least one final state, with terminal: true");
    }
    Set<String> leaving = new HashSet<>();
    for (Map.Entry<String, ActionDraft> entry : actions.entrySet()) {
      ActionDraft draft = entry.getValue();
      Node at = draft.node == null ? process : draft.node;
      for (String from : draft.from) {
        State state = states.get(from);
        if (state == null) {
          problem(draft.fromNode == null ? at : draft.fromNode,
              "action " + entry.getKey() + " leaves " + from + ", which is not a state, states: "
                  + String.join(", ", states.keySet()));
        } else if (state.terminal()) {
          problem(draft.fromNode == null ? at : draft.fromNode,
              "action " + entry.getKey() + " leaves the final state " + from);
        } else if (!ProcessFamily.WITHDRAW.equals(entry.getKey())) {
          leaving.add(from);
        }
      }
      if (!ProcessFamily.WITHDRAW.equals(entry.getKey()) && !states.containsKey(draft.to)) {
        problem(draft.toNode == null ? at : draft.toNode, "action " + entry.getKey() + " ends in "
            + draft.to + ", which is not a state, states: " + String.join(", ", states.keySet()));
      }
      if (draft.by == Actor.OWNER && !draft.roles.isEmpty()) {
        problem(at, "action " + entry.getKey() + " is taken by the owner, so it takes no role");
      }
    }
    if (start == null || problems.size() > before) {
      return;
    }
    for (State state : states.values()) {
      if (!state.terminal() && !leaving.contains(state.name())) {
        problem(nodes.getOrDefault("state:" + state.name(), process),
            "state " + state.name() + " has no action leaving it, so a record would stay there");
      }
    }
    Set<String> reached = new HashSet<>(Set.of(initial));
    boolean grown = true;
    while (grown) {
      grown = false;
      for (Map.Entry<String, ActionDraft> entry : actions.entrySet()) {
        ActionDraft draft = entry.getValue();
        if (!ProcessFamily.WITHDRAW.equals(entry.getKey())
            && draft.from.stream().anyMatch(reached::contains) && reached.add(draft.to)) {
          grown = true;
        }
      }
    }
    for (State state : states.values()) {
      if (!reached.contains(state.name())) {
        problem(nodes.getOrDefault("state:" + state.name(), process),
            "state " + state.name() + " can never be reached from " + initial);
      }
    }
  }

  private static boolean isNull(Node node) {
    return node instanceof ScalarNode scalar && Tag.NULL.equals(scalar.getTag());
  }

  private Booking readBooking(MappingNode process, Node node, String family,
      Map<String, Field> fields) {
    boolean reservation = "reservation".equals(family);
    if (node == null) {
      if (reservation) {
        problem(process, "the reservation family needs a booking block");
      }
      return null;
    }
    if (!reservation) {
      problem(node, "booking only applies to the reservation family");
      return null;
    }
    if (!(node instanceof MappingNode map)) {
      problem(node, "booking is a mapping with resource, starts, ends and capacity");
      return null;
    }
    Map<String, Node> keys = mapping(map, Set.of("resource", "starts", "ends", "capacity"));
    String resource =
        bookingField(map, keys.get("resource"), "resource", FieldType.REFERENCE, fields);
    String starts = bookingField(map, keys.get("starts"), "starts", FieldType.DATETIME, fields);
    String ends = bookingField(map, keys.get("ends"), "ends", FieldType.DATETIME, fields);
    if (starts != null && starts.equals(ends)) {
      problem(keys.get("ends"), "starts and ends must be different fields");
    }
    int capacity = 1;
    Node capacityNode = keys.get("capacity");
    if (capacityNode != null) {
      String text = scalar(capacityNode);
      if (!text.matches("[0-9]{1,6}") || Integer.parseInt(text) < 1) {
        problem(capacityNode, "capacity is a positive whole number");
      } else {
        capacity = Integer.parseInt(text);
      }
    }
    return resource == null || starts == null || ends == null ? null
        : new Booking(resource, starts, ends, capacity);
  }

  private String bookingField(MappingNode booking, Node node, String key, FieldType type,
      Map<String, Field> fields) {
    String name = node == null ? null : scalar(node);
    Field field = name == null ? null : fields.get(name);
    if (field == null || field.type() != type) {
      problem(node == null ? booking : node,
          key + " must name a " + type.key() + " field of this entity");
      return null;
    }
    return name;
  }

  private Map<String, List<Completion>> readAfter(Node node, ProcessFamily family,
      Map<String, Field> fields) {
    Map<String, List<Completion>> after = new LinkedHashMap<>();
    if (node == null) {
      return after;
    }
    if (!(node instanceof MappingNode states)) {
      problem(node, "after maps a final state to a list of actions");
      return after;
    }
    for (NodeTuple tuple : states.getValue()) {
      String state = scalar(tuple.getKeyNode());
      if (!family.isTerminal(state)) {
        problem(tuple.getKeyNode(), state + " is not a final state of the process");
        continue;
      }
      if (!(tuple.getValueNode() instanceof SequenceNode list)) {
        problem(tuple.getValueNode(), "a list of actions is required");
        continue;
      }
      List<Completion> items = new ArrayList<>();
      for (Node item : list.getValue()) {
        Completion completion = readCompletion(item, fields);
        if (completion != null) {
          items.add(completion);
        }
      }
      after.put(state, items);
    }
    return after;
  }

  private Completion readCompletion(Node node, Map<String, Field> fields) {
    if (!(node instanceof MappingNode map)) {
      problem(node, "an action is a mapping with do, user and group or group_from");
      return null;
    }
    Map<String, Node> keys = mapping(map, Set.of("do", "user", "group", "group_from"));
    Node doNode = keys.get("do");
    String operation = doNode == null ? null : scalar(doNode);
    if (operation == null || !Completion.OPERATIONS.contains(operation)) {
      problem(doNode == null ? map : doNode, "unknown action, available: "
          + String.join(", ", Completion.OPERATIONS.stream().sorted().toList()));
      return null;
    }
    Node userNode = keys.get("user");
    String user = userNode == null ? null : scalar(userNode);
    Field userField = user == null ? null : fields.get(user);
    if (user == null || (!Completion.CREATOR.equals(user)
        && (userField == null || userField.type() != FieldType.USER))) {
      problem(userNode == null ? map : userNode, "user must be created_by or a user field");
      return null;
    }
    Node groupNode = keys.get("group");
    Node groupFieldNode = keys.get("group_from");
    if ((groupNode == null) == (groupFieldNode == null)) {
      problem(map, "give either group or group_from");
      return null;
    }
    String groupField = groupFieldNode == null ? null : scalar(groupFieldNode);
    if (groupField != null) {
      Field field = fields.get(groupField);
      if (field == null || (field.type() != FieldType.CHOICE && field.type() != FieldType.TEXT)) {
        problem(groupFieldNode, "group_from must be a choice or text field");
        return null;
      }
    }
    return new Completion(operation, user, groupNode == null ? null : scalar(groupNode),
        groupField);
  }

  // A number followed by m (minutes), h (hours) or d (days), such as 90m, 72h or 3d.
  private Duration deadline(Node node) {
    return duration(node, "deadline", "72h");
  }

  // How long a record waits for its owner before the system takes an action for them: a time
  // alone, for a family that has such an action, or a mapping with the time and the action.
  private AutoClose readAutoClose(Node node, String family, ProcessFamily definition) {
    if (node == null) {
      return new AutoClose(null, null);
    }
    if (node instanceof MappingNode map) {
      Map<String, Node> keys = mapping(map, ProcessSyntax.AUTO_CLOSE);
      Duration after =
          keys.get("after") == null ? null : duration(keys.get("after"), "after", "7d");
      if (keys.get("after") == null) {
        problem(map, "auto_close needs after, the time to wait");
      }
      String action = keys.get("action") == null ? null : scalar(keys.get("action"));
      if (action == null) {
        problem(map, "auto_close needs action, an action of the owner");
        return new AutoClose(null, null);
      }
      return checkedAutoClose(after, action, keys.get("action"), definition);
    }
    Duration after = duration(node, "auto_close", "7d");
    String action =
        family == null ? null : ProcessFamily.defaultAutoCloseAction(family).orElse(null);
    if (action == null) {
      problem(node,
          "auto_close needs an action here, as auto_close: {after: 7d, action: <action>}");
      return new AutoClose(null, null);
    }
    return checkedAutoClose(after, action, node, definition);
  }

  private AutoClose checkedAutoClose(Duration after, String action, Node node,
      ProcessFamily definition) {
    Action found = definition.action(action).orElse(null);
    if (found == null || found.by() != Actor.OWNER || ProcessFamily.WITHDRAW.equals(action)) {
      problem(node, "the action of auto_close must be an action of the owner other than withdraw");
      return new AutoClose(null, null);
    }
    return new AutoClose(after, action);
  }

  private record AutoClose(Duration after, String action) {}

  private Duration duration(Node node, String key, String example) {
    if (node == null) {
      return null;
    }
    String text = scalar(node);
    Matcher matcher = DEADLINE.matcher(text == null ? "" : text);
    if (!matcher.matches() || Long.parseLong(matcher.group(1)) == 0) {
      problem(node, key + " is a positive number followed by m, h or d, such as " + example);
      return null;
    }
    long amount = Long.parseLong(matcher.group(1));
    return switch (matcher.group(2)) {
      case "m" -> Duration.ofMinutes(amount);
      case "h" -> Duration.ofHours(amount);
      default -> Duration.ofDays(amount);
    };
  }

  private boolean flag(Node node, String what) {
    if (node == null) {
      return false;
    }
    String value = scalar(node);
    if (!"true".equals(value) && !"false".equals(value)) {
      problem(node, what + " must be true or false");
    }
    return "true".equals(value);
  }

  // The values of a mapping by key; unknown keys are problems.
  private Map<String, Node> mapping(MappingNode node, Set<String> allowed) {
    Map<String, Node> keys = new LinkedHashMap<>();
    for (NodeTuple tuple : node.getValue()) {
      String key = scalar(tuple.getKeyNode());
      if (!allowed.contains(key)) {
        problem(tuple.getKeyNode(), "unknown key " + key + ", allowed: "
            + String.join(", ", allowed.stream().sorted().toList()));
        continue;
      }
      keys.put(key, tuple.getValueNode());
    }
    return keys;
  }

  private static String scalar(Node node) {
    return node instanceof ScalarNode scalar ? scalar.getValue() : "";
  }

  private void problem(Node node, String message) {
    if (node == null || node.getStartMark() == null) {
      problems.add(new Problem(0, 0, message));
      return;
    }
    problems.add(new Problem(node.getStartMark().getLine() + 1, node.getStartMark().getColumn() + 1,
        message));
  }
}
