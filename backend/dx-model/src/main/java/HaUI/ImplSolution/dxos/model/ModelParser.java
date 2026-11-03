// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import HaUI.ImplSolution.dxos.model.Model.Process;
import HaUI.ImplSolution.dxos.model.Model.Role;
import HaUI.ImplSolution.dxos.model.Model.Unit;
import HaUI.ImplSolution.dxos.model.ModelException.Problem;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
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

// Reads a model file and checks it. Every problem carries its line and column; nothing outside the
// file is touched.
public final class ModelParser {

  public static final int FORMAT = 0;

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,39}");
  private static final Pattern DEADLINE = Pattern.compile("([0-9]{1,6})([mhd])");
  private static final Pattern ROLE = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");
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
    Map<String, Node> keys = mapping(top, Set.of("format", "name", "roles", "units", "entities"));

    int format = readFormat(top, keys.get("format"));
    String name = readName(top, keys.get("name"), "model name");
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
    return new Model(format, name, List.copyOf(roles), List.copyOf(units), entities);
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

  // A scope needs what it filters on, on the entity or on the record the entity belongs to.
  private void checkScopes(Map<String, Entity> entities) {
    for (Entity entity : entities.values()) {
      Entity holder = entity;
      if (entity.ownerVia() != null) {
        Field via = entity.fields().get(entity.ownerVia());
        holder = via == null ? null : entities.get(via.to());
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

  private Process readProcess(Node node, Set<String> roleNames, Map<String, Scope> access,
      Map<String, Field> fields) {
    if (node == null) {
      return null;
    }
    if (!(node instanceof MappingNode map)) {
      problem(node, "process is a mapping with family and handler");
      return null;
    }
    Map<String, Node> keys =
        mapping(map, Set.of("family", "handler", "allow_self_decision", "deadline", "after"));
    Node familyNode = keys.get("family");
    String family = familyNode == null ? null : scalar(familyNode);
    if (family == null || ProcessFamily.find(family).isEmpty()) {
      problem(familyNode == null ? map : familyNode,
          "unknown process family, available: " + String.join(", ", ProcessFamily.names()));
      return null;
    }
    Node handlerNode = keys.get("handler");
    String handler = handlerNode == null ? null : scalar(handlerNode);
    if (handler == null || !roleNames.contains(handler)) {
      problem(handlerNode == null ? map : handlerNode, "handler must be a role declared in roles");
      return null;
    }
    Scope handlerScope = access.get(handler);
    if (handlerScope == null || handlerScope == Scope.OWN) {
      problem(handlerNode,
          "the handler role " + handler + " must have access all, read, unit or assigned");
    }
    return new Process(family, handler,
        flag(keys.get("allow_self_decision"), "allow_self_decision"),
        deadline(keys.get("deadline")),
        readAfter(keys.get("after"), ProcessFamily.find(family).orElseThrow(), fields));
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
        problem(tuple.getKeyNode(), state + " is not a final state of the family " + family.name());
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
    if (node == null) {
      return null;
    }
    String text = scalar(node);
    Matcher matcher = DEADLINE.matcher(text == null ? "" : text);
    if (!matcher.matches() || Long.parseLong(matcher.group(1)) == 0) {
      problem(node, "deadline is a positive number followed by m, h or d, such as 72h");
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
