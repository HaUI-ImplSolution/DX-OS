// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import HaUI.ImplSolution.dxos.model.Model.Process;
import HaUI.ImplSolution.dxos.model.Model.Role;
import HaUI.ImplSolution.dxos.model.ModelException.Problem;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private static final Pattern ROLE = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");
  private static final Set<String> RESERVED_FIELDS =
      Set.of("id", "created_by", "created_at", "updated_at", "version", "status");
  private static final Set<String> PROCESS_TEMPLATES = Set.of("approval");

  private final List<Problem> problems = new ArrayList<>();

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
    Map<String, Node> keys = mapping(top, Set.of("format", "name", "roles", "entities"));

    int format = readFormat(top, keys.get("format"));
    String name = readName(top, keys.get("name"), "model name");
    List<Role> roles = readRoles(top, keys.get("roles"));
    Set<String> roleNames = new LinkedHashSet<>();
    roles.forEach(role -> roleNames.add(role.name()));

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
    if (!problems.isEmpty()) {
      return null;
    }
    return new Model(format, name, List.copyOf(roles), entities);
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
    Process process = readProcess(keys.get("process"), roleNames, access);

    String ownerVia = null;
    Node ownerNode = keys.get("owner_via");
    if (ownerNode != null) {
      ownerVia = scalar(ownerNode);
      Field target = fields.get(ownerVia);
      if (target == null || target.type() != FieldType.REFERENCE) {
        problem(ownerNode, "owner_via must name a reference field of " + name);
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
    Map<String, Node> keys =
        mapping(spec, Set.of("type", "label", "required", "unique", "options", "to"));
    Node typeNode = keys.get("type");
    if (typeNode == null) {
      problem(spec, "field " + name + " has no type");
      return null;
    }
    FieldType type = FieldType.of(scalar(typeNode)).orElse(null);
    if (type == null) {
      problem(typeNode, "unknown type " + scalar(typeNode)
          + ", use text, number, datetime, choice, reference or user");
      return null;
    }
    List<String> options = List.of();
    if (type == FieldType.CHOICE) {
      options = readOptions(spec, keys.get("options"));
    } else if (keys.get("options") != null) {
      problem(keys.get("options"), "options only applies to a choice field");
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
    Node label = keys.get("label");
    return new Field(name, type, label == null ? name : scalar(label), required, unique, options,
        to);
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
      problem(node, "access maps at least one role to all, own or read");
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
        problem(tuple.getValueNode(), "access of " + role + " must be all, own or read");
        continue;
      }
      access.put(role, scope);
    }
    return access;
  }

  private Process readProcess(Node node, Set<String> roleNames, Map<String, Scope> access) {
    if (node == null) {
      return null;
    }
    if (!(node instanceof MappingNode map)) {
      problem(node, "process is a mapping with template and approver");
      return null;
    }
    Map<String, Node> keys = mapping(map, Set.of("template", "approver", "allow_self_decision"));
    Node templateNode = keys.get("template");
    String template = templateNode == null ? null : scalar(templateNode);
    if (template == null || !PROCESS_TEMPLATES.contains(template)) {
      problem(templateNode == null ? map : templateNode,
          "unknown process template, available: " + String.join(", ", PROCESS_TEMPLATES));
      return null;
    }
    Node approverNode = keys.get("approver");
    String approver = approverNode == null ? null : scalar(approverNode);
    if (approver == null || !roleNames.contains(approver)) {
      problem(approverNode == null ? map : approverNode,
          "approver must be a role declared in roles");
      return null;
    }
    Scope approverScope = access.get(approver);
    if (approverScope == null || !approverScope.reachesAll()) {
      problem(approverNode, "the approver role " + approver + " must have access all or read");
    }
    return new Process(template, approver,
        flag(keys.get("allow_self_decision"), "allow_self_decision"));
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
