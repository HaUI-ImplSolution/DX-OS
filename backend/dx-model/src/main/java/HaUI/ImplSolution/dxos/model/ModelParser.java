// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import HaUI.ImplSolution.dxos.model.Model.Document;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import HaUI.ImplSolution.dxos.model.Model.Process;
import HaUI.ImplSolution.dxos.model.Model.Role;
import HaUI.ImplSolution.dxos.model.Model.Ui;
import HaUI.ImplSolution.dxos.model.Model.Ui.FormGroup;
import HaUI.ImplSolution.dxos.model.Model.Ui.FormView;
import HaUI.ImplSolution.dxos.model.Model.Ui.ListView;
import HaUI.ImplSolution.dxos.model.Model.Ui.Menu;
import HaUI.ImplSolution.dxos.model.Model.Unit;
import HaUI.ImplSolution.dxos.model.ModelException.Problem;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Action;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Actor;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Condition;
import HaUI.ImplSolution.dxos.model.ProcessFamily.State;
import java.io.StringReader;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
import java.util.regex.PatternSyntaxException;
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

  public static final int FORMAT = ModelUpgrader.CURRENT_FORMAT;

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,39}");
  private static final Pattern DEADLINE = Pattern.compile("([0-9]{1,6})([mhd])");
  private static final Pattern ROLE = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");
  // Roles the platform owns: a model that declared one would hand platform rights to its users.
  private static final Set<String> RESERVED_ROLES = Set.of("ADMIN");
  private static final Set<String> RESERVED_FIELDS =
      Set.of(
          "id",
          "created_by",
          "created_at",
          "updated_at",
          "version",
          "status",
          "assignee",
          "decided_by",
          "decided_at",
          "handle_by",
          "overdue");

  // The columns a record of an entity with a process has besides its fields.
  private static final List<String> PROCESS_COLUMNS =
      List.of("id", "status", "created_at", "updated_at", "decided_at");

  private final List<Problem> problems = new ArrayList<>();

  private final Set<String> unitNames = new LinkedHashSet<>();
  private final Map<String, Node> accessNodes = new LinkedHashMap<>();

  private ModelParser() {}

  public static Model parse(String yaml) {
    ModelParser parser = new ModelParser();
    Model model = parser.read(ModelUpgrader.upgrade(yaml).content());
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
        problem(
            nameNode == null ? map : nameNode,
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
        problem(
            accessNodes.get(entity.name()),
            "owner_via of " + entity.name() + " leads back to " + entity.name());
        continue;
      }
      if (entity.fields().values().stream().filter(f -> f.type() == FieldType.UNIT).count() > 1) {
        problem(
            accessNodes.get(entity.name()),
            "entity " + entity.name() + " has more than one unit field");
      }
      if (holder == null) {
        continue;
      }
      for (Map.Entry<String, Scope> grant : entity.access().entrySet()) {
        if (grant.getValue() == Scope.UNIT && holder.unitField().isEmpty()) {
          problem(
              accessNodes.get(entity.name()),
              "access unit of " + grant.getKey() + " needs a unit field on " + holder.name());
        }
        if (grant.getValue() == Scope.ASSIGNED && holder.process() == null) {
          problem(
              accessNodes.get(entity.name()),
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
        problem(
            nameNode == null ? map : nameNode,
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
        mapping(
            map, Set.of("label", "fields", "access", "process", "owner_via", "ui", "documents"));

    Map<String, Field> fields = readFields(map, keys.get("fields"), entityNames);
    if (fields.values().stream().filter(field -> field.type() == FieldType.CODE).count() > 1) {
      problem(keys.get("fields"), "an entity has one code field at most");
    }
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
        problem(
            keys.get("fields"),
            "field "
                + field.name()
                + " is renamed from "
                + field.renamedFrom()
                + ", which still exists");
      }
    }
    Ui ui = readUi(keys.get("ui"), fields, process != null);
    List<Document> documents = readDocuments(keys.get("documents"), fields, process);
    Node label = keys.get("label");
    return new Entity(
        name,
        label == null ? name : scalar(label),
        fields,
        access,
        process,
        ownerVia,
        ui,
        documents);
  }

  // The print templates of an entity. A placeholder names a field of the entity or one of the
  // columns every record has, so a template never reaches data outside the record.
  private List<Document> readDocuments(Node node, Map<String, Field> fields, Process process) {
    if (node == null) {
      return List.of();
    }
    if (!(node instanceof MappingNode map)) {
      problem(node, "documents is a mapping of document names to templates");
      return List.of();
    }
    Set<String> known = new LinkedHashSet<>(fields.keySet());
    known.addAll(process == null ? List.of("id", "created_at", "updated_at") : PROCESS_COLUMNS);
    if (map.getValue().size() > Document.MAX_PER_ENTITY) {
      problem(node, "an entity has at most " + Document.MAX_PER_ENTITY + " documents");
    }
    List<Document> documents = new ArrayList<>();
    Set<String> names = new LinkedHashSet<>();
    for (NodeTuple tuple : map.getValue()) {
      String name = scalar(tuple.getKeyNode());
      if (!NAME.matcher(name).matches()) {
        problem(
            tuple.getKeyNode(),
            "document name must match [a-z][a-z0-9_]* and be at most 40 characters");
      } else if (!names.add(name)) {
        problem(tuple.getKeyNode(), "document " + name + " is declared twice");
      } else {
        Document document = readDocument(name, tuple.getValueNode(), known, process);
        if (document != null) {
          documents.add(document);
        }
      }
    }
    return documents;
  }

  private Document readDocument(String name, Node node, Set<String> known, Process process) {
    if (!(node instanceof MappingNode map)) {
      problem(node, "a document is a mapping with label, file, when, title and body");
      return null;
    }
    Map<String, Node> keys = mapping(map, Set.of("label", "file", "when", "title", "body"));
    String label = readDocumentText(keys.get("label"), name, known);
    if (Document.PLACEHOLDER.matcher(label).find()) {
      problem(keys.get("label"), "label holds no placeholders");
    }
    String file = readDocumentText(keys.get("file"), name, known);
    if (!Document.PLACEHOLDER.matcher(file).replaceAll("").matches("[A-Za-z0-9._-]*")) {
      problem(
          keys.get("file"),
          "file may hold letters, digits, dot, dash, underscore and placeholders only");
    }
    if (file.length() > Document.MAX_FILE) {
      problem(keys.get("file"), "file is at most " + Document.MAX_FILE + " characters");
    }
    List<String> when = List.of();
    Node whenNode = keys.get("when");
    if (whenNode != null) {
      if (process == null) {
        problem(whenNode, "when names states, and this entity has no process");
      } else {
        when = readNames(whenNode, "when");
        List<String> states =
            process.definition().states().stream().map(ProcessFamily.State::name).toList();
        for (String state : when) {
          if (!states.contains(state)) {
            problem(
                whenNode,
                "when names the state "
                    + state
                    + " that the process does not have, use "
                    + String.join(", ", states));
          }
        }
      }
    }
    String title =
        keys.get("title") == null ? null : readDocumentText(keys.get("title"), null, known);
    List<Document.Block> body = readBlocks(keys.get("body"), known);
    if (keys.get("body") == null) {
      problem(node, "document " + name + " needs a body");
    }
    return new Document(
        name, label.isEmpty() ? name : label, file.isEmpty() ? name : file, when, title, body);
  }

  private List<Document.Block> readBlocks(Node node, Set<String> known) {
    if (node == null) {
      return List.of();
    }
    if (!(node instanceof SequenceNode sequence) || sequence.getValue().isEmpty()) {
      problem(node, "body is a list of blocks: heading, text, fields or signatures");
      return List.of();
    }
    if (sequence.getValue().size() > Document.MAX_BLOCKS) {
      problem(node, "body has at most " + Document.MAX_BLOCKS + " blocks");
    }
    List<Document.Block> blocks = new ArrayList<>();
    for (Node item : sequence.getValue()) {
      if (!(item instanceof MappingNode block) || block.getValue().size() != 1) {
        problem(item, "a block is a mapping with one key: heading, text, fields or signatures");
        continue;
      }
      NodeTuple tuple = block.getValue().get(0);
      Node value = tuple.getValueNode();
      switch (scalar(tuple.getKeyNode())) {
        case "heading" -> blocks.add(new Document.Heading(readDocumentText(value, null, known)));
        case "text" -> blocks.add(new Document.Text(readDocumentText(value, null, known)));
        case "fields" -> {
          List<String> names = readDocumentNames(value, known, "fields", Integer.MAX_VALUE);
          if (!names.isEmpty()) {
            blocks.add(new Document.Fields(names));
          }
        }
        case "signatures" -> {
          List<String> captions = new ArrayList<>();
          if (value instanceof SequenceNode list
              && !list.getValue().isEmpty()
              && list.getValue().size() <= Document.MAX_SIGNATURES) {
            list.getValue()
                .forEach(caption -> captions.add(readDocumentText(caption, null, known)));
            blocks.add(new Document.Signatures(captions));
          } else {
            problem(value, "signatures is a list of 1 to " + Document.MAX_SIGNATURES + " captions");
          }
        }
        default ->
            problem(
                tuple.getKeyNode(),
                "unknown block "
                    + scalar(tuple.getKeyNode())
                    + ", use heading, text, fields or signatures");
      }
    }
    return blocks;
  }

  private List<String> readDocumentNames(Node node, Set<String> known, String key, int max) {
    if (!(node instanceof SequenceNode list)
        || list.getValue().isEmpty()
        || list.getValue().size() > max) {
      problem(node, key + " is a list of field names");
      return List.of();
    }
    List<String> names = new ArrayList<>();
    for (Node item : list.getValue()) {
      String field = scalar(item);
      if (!known.contains(field)) {
        problem(item, key + " names " + field + ", which is not a field of this entity");
      } else if (!names.contains(field)) {
        names.add(field);
      }
    }
    return names;
  }

  // A text of a template: a scalar without control characters other than line breaks whose
  // placeholders name known fields.
  private String readDocumentText(Node node, String fallback, Set<String> known) {
    if (node == null) {
      return fallback == null ? "" : fallback;
    }
    if (!(node instanceof ScalarNode scalar)) {
      problem(node, "this is a text");
      return fallback == null ? "" : fallback;
    }
    String text = scalar.getValue();
    if (text.length() > Document.MAX_TEXT) {
      problem(node, "a text is at most " + Document.MAX_TEXT + " characters");
    }
    if (text.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n')) {
      problem(node, "a text holds no control characters other than line breaks");
    }
    var placeholder = Document.PLACEHOLDER.matcher(text);
    while (placeholder.find()) {
      if (!known.contains(placeholder.group(1))) {
        problem(
            node,
            "placeholder {{"
                + placeholder.group(1)
                + "}} is not a field of this entity or one of its columns");
      }
    }
    String rest = Document.PLACEHOLDER.matcher(text).replaceAll("");
    if (rest.contains("{{") || rest.contains("}}")) {
      problem(node, "a placeholder is written {{field_name}}");
    }
    return text;
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
    Map<String, Node> requiredWhen = new LinkedHashMap<>();
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
        for (NodeTuple part : spec.getValue()) {
          if ("required_when".equals(scalar(part.getKeyNode()))) {
            requiredWhen.put(name, part.getValueNode());
          }
        }
      }
    }
    // The condition names other fields of the entity, so it is read once every field is known.
    requiredWhen.forEach(
        (name, whenNode) -> {
          Field field = fields.get(name);
          Condition condition = readRequiredWhen(whenNode, field, fields);
          if (condition != null) {
            fields.put(
                name,
                new Field(
                    field.name(),
                    field.type(),
                    field.label(),
                    field.required(),
                    field.unique(),
                    field.options(),
                    field.to(),
                    field.renamedFrom(),
                    field.defaultValue(),
                    field.multiple(),
                    field.min(),
                    field.max(),
                    field.pattern(),
                    condition,
                    field.format()));
          }
        });
    return fields;
  }

  private Field readField(String name, MappingNode spec, Set<String> entityNames) {
    Map<String, Node> keys =
        mapping(
            spec,
            Set.of(
                "type",
                "label",
                "required",
                "unique",
                "options",
                "to",
                "renamed_from",
                "default",
                "multiple",
                "min",
                "max",
                "pattern",
                "required_when",
                "format"));
    Node typeNode = keys.get("type");
    if (typeNode == null) {
      problem(spec, "field " + name + " has no type");
      return null;
    }
    FieldType type = FieldType.of(scalar(typeNode)).orElse(null);
    if (type == null) {
      problem(
          typeNode,
          "unknown type "
              + scalar(typeNode)
              + ", use text, number, datetime, date, boolean, choice, reference, user, unit, file"
              + " or code");
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
        problem(
            toNode == null ? spec : toNode,
            "a reference field needs `to` naming an entity of this model");
      }
    } else if (keys.get("to") != null) {
      problem(keys.get("to"), "to only applies to a reference field");
    }
    String format = null;
    if (type == FieldType.CODE) {
      format = readCodeFormat(spec, keys);
    } else if (keys.get("format") != null) {
      problem(keys.get("format"), "format only applies to a code field");
    }
    boolean required = flag(keys.get("required"), "required");
    boolean unique = flag(keys.get("unique"), "unique") || type == FieldType.CODE;
    boolean multiple = flag(keys.get("multiple"), "multiple");
    if (multiple && type != FieldType.CHOICE) {
      problem(keys.get("multiple"), "multiple only applies to a choice field");
      multiple = false;
    }
    if (multiple && unique) {
      problem(keys.get("unique"), "a field with several choices cannot be unique");
    }
    if (type == FieldType.FILE && (required || unique)) {
      problem(spec, "a file field is filled by uploading, so it cannot be required or unique");
    }
    if (type == FieldType.BOOLEAN && (required || unique)) {
      problem(
          spec,
          "a boolean field is always true or false, so it cannot be required or unique;"
              + " give it a default instead");
    }
    String renamedFrom = readRenamedFrom(name, keys.get("renamed_from"));
    String defaultValue =
        type == FieldType.CODE
            ? null
            : readDefault(type, options, unique, multiple, keys.get("default"));
    String min = readBound(type, keys.get("min"), "min");
    String max = readBound(type, keys.get("max"), "max");
    if (min != null && max != null && compareBounds(type, min, max) > 0) {
      problem(keys.get("min"), "min is above max");
    }
    String pattern = readPattern(type, keys.get("pattern"));
    checkDefault(keys.get("default"), type, defaultValue, min, max, pattern);
    Node label = keys.get("label");
    return new Field(
        name,
        type,
        label == null ? name : scalar(label),
        required,
        unique,
        options,
        to,
        renamedFrom,
        defaultValue,
        multiple,
        min,
        max,
        pattern,
        null,
        format);
  }

  // The format of a code field. The system assigns the value, so nothing that describes a value
  // given by a person applies.
  private String readCodeFormat(MappingNode spec, Map<String, Node> keys) {
    for (String key :
        List.of(
            "required",
            "unique",
            "options",
            "to",
            "renamed_from",
            "default",
            "multiple",
            "min",
            "max",
            "pattern",
            "required_when")) {
      if (keys.get(key) != null) {
        problem(keys.get(key), key + " does not apply to a code field, which the system assigns");
      }
    }
    Node node = keys.get("format");
    if (node == null) {
      problem(spec, "a code field needs a format such as HS-{year}-{seq:5}");
      return null;
    }
    try {
      return CodeFormat.parse(scalar(node)).text();
    } catch (IllegalArgumentException e) {
      problem(node, e.getMessage());
      return null;
    }
  }

  // The bound of a number or date field, in the canonical text of the value.
  private String readBound(FieldType type, Node node, String key) {
    if (node == null) {
      return null;
    }
    String value = scalar(node);
    if (type == FieldType.NUMBER) {
      try {
        return new java.math.BigDecimal(value).stripTrailingZeros().toPlainString();
      } catch (NumberFormatException e) {
        problem(node, key + " of a number field must be a number");
        return null;
      }
    }
    if (type == FieldType.DATE) {
      try {
        return LocalDate.parse(value).toString();
      } catch (DateTimeParseException e) {
        problem(node, key + " of a date field must be a date like 2026-01-31");
        return null;
      }
    }
    problem(node, key + " only applies to a number or date field");
    return null;
  }

  private static int compareBounds(FieldType type, String left, String right) {
    return type == FieldType.NUMBER
        ? new java.math.BigDecimal(left).compareTo(new java.math.BigDecimal(right))
        : LocalDate.parse(left).compareTo(LocalDate.parse(right));
  }

  // A default has to meet the bounds and the pattern of its own field.
  private void checkDefault(
      Node node, FieldType type, String value, String min, String max, String pattern) {
    if (node == null || value == null) {
      return;
    }
    if (type == FieldType.NUMBER || type == FieldType.DATE) {
      String canonical = type == FieldType.NUMBER ? canonicalNumber(value) : value;
      if (canonical == null) {
        return;
      }
      if (min != null && compareBounds(type, canonical, min) < 0) {
        problem(node, "the default is below min");
      }
      if (max != null && compareBounds(type, canonical, max) > 0) {
        problem(node, "the default is above max");
      }
    } else if (type == FieldType.TEXT && pattern != null && !Pattern.matches(pattern, value)) {
      problem(node, "the default does not match the pattern");
    }
  }

  private static String canonicalNumber(String value) {
    try {
      return new java.math.BigDecimal(value).stripTrailingZeros().toPlainString();
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static final int PATTERN_LIMIT = 200;
  // PostgreSQL refuses a repeat count above this.
  private static final int PATTERN_REPEAT = 255;

  private String readPattern(FieldType type, Node node) {
    if (node == null) {
      return null;
    }
    if (type != FieldType.TEXT) {
      problem(node, "pattern only applies to a text field");
      return null;
    }
    String pattern = scalar(node);
    String reason = patternProblem(pattern);
    if (reason != null) {
      problem(node, reason);
      return null;
    }
    return pattern;
  }

  // The patterns Java and PostgreSQL read the same way: literal characters, a dot, bracket classes
  // of characters and ranges, groups, alternation and the quantifiers * + ? {n} {n,m} with counts
  // up
  // to 255. The pattern has to match the whole value, so it carries no ^ or $. A group that holds a
  // quantifier is not itself repeated by * + or {n,m}, which keeps a match from backtracking
  // without
  // end.
  static String patternProblem(String pattern) {
    if (pattern.isEmpty() || pattern.length() > PATTERN_LIMIT) {
      return "pattern is 1 to " + PATTERN_LIMIT + " characters";
    }
    boolean inClass = false;
    // One entry per open group: whether a quantifier sits inside it.
    java.util.ArrayDeque<Boolean> groups = new java.util.ArrayDeque<>();
    boolean afterQuantifiedGroup = false;
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      char next = i + 1 < pattern.length() ? pattern.charAt(i + 1) : 0;
      if (c == '\\') {
        if (next == 0 || Character.isLetterOrDigit(next)) {
          return "pattern may escape only punctuation; write [0-9] instead of \\d";
        }
        i++;
        afterQuantifiedGroup = false;
        continue;
      }
      if (inClass) {
        if (c == '[') {
          return "pattern has a bracket class inside a bracket class";
        }
        if (c == '&' && next == '&') {
          return "pattern has && inside a bracket class";
        }
        inClass = c != ']';
        continue;
      }
      boolean repeats = false;
      boolean quantifier = true;
      switch (c) {
        case '*', '+' -> repeats = true;
        case '?' -> repeats = false;
        case '{' -> {
          int close = pattern.indexOf('}', i);
          java.util.regex.Matcher count =
              close < 0
                  ? null
                  : Pattern.compile("([0-9]{1,4})(?:(,)([0-9]{0,4}))?")
                      .matcher(pattern.substring(i + 1, close));
          if (count == null || !count.matches()) {
            return "pattern has a { that is not a repeat like {3} or {2,5}";
          }
          int low = Integer.parseInt(count.group(1));
          Integer high =
              count.group(2) == null
                  ? Integer.valueOf(low)
                  : count.group(3).isEmpty() ? null : Integer.valueOf(count.group(3));
          if (low > PATTERN_REPEAT || high != null && high > PATTERN_REPEAT) {
            return "a repeat in pattern counts up to " + PATTERN_REPEAT;
          }
          if (high != null && high < low) {
            return "a repeat in pattern has its upper count below the lower";
          }
          repeats = high == null || high > 1;
          i = close;
          next = i + 1 < pattern.length() ? pattern.charAt(i + 1) : 0;
        }
        default -> quantifier = false;
      }
      if (quantifier) {
        if (next == '?' || next == '+') {
          return "pattern has no lazy or possessive quantifiers";
        }
        if (repeats && afterQuantifiedGroup) {
          return "pattern repeats a group that holds a repeat; write the group without the inner"
              + " repeat or leave the outer repeat out";
        }
        if (!groups.isEmpty()) {
          groups.pop();
          groups.push(true);
        }
        afterQuantifiedGroup = false;
        continue;
      }
      afterQuantifiedGroup = false;
      switch (c) {
        case '[' -> inClass = true;
        case '(' -> {
          if (next == '?') {
            return "pattern has no (? groups, lookarounds or flags";
          }
          groups.push(false);
        }
        case ')' -> {
          if (groups.isEmpty()) {
            return "pattern has a ) without a (";
          }
          boolean held = groups.pop();
          if (held && !groups.isEmpty()) {
            groups.pop();
            groups.push(true);
          }
          afterQuantifiedGroup = held;
        }
        case '^', '$' -> {
          return "pattern matches the whole value, so leave out ^ and $";
        }
        default -> {}
      }
    }
    if (inClass) {
      return "pattern has a [ without a ]";
    }
    if (!groups.isEmpty()) {
      return "pattern has a ( without a )";
    }
    try {
      Pattern.compile(pattern);
    } catch (PatternSyntaxException e) {
      return "pattern is not a valid expression: " + e.getDescription();
    }
    return null;
  }

  // required_when makes a field required for the records that meet one comparison on another field.
  private Condition readRequiredWhen(Node node, Field field, Map<String, Field> fields) {
    if (field.required()) {
      problem(node, "required_when and required cannot both be set");
      return null;
    }
    if (field.type() == FieldType.FILE || field.type() == FieldType.BOOLEAN) {
      problem(node, "required_when does not apply to a " + field.type().key() + " field");
      return null;
    }
    Condition condition = readCondition(node, fields, "required_when");
    if (condition != null && condition.field().equals(field.name())) {
      problem(node, "required_when cannot compare the field with itself");
      return null;
    }
    return condition;
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

  private String readDefault(
      FieldType type, List<String> options, boolean unique, boolean multiple, Node node) {
    if (node == null) {
      return null;
    }
    if (multiple) {
      return readChoicesDefault(options, unique, node);
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
    } else if (type == FieldType.BOOLEAN) {
      if (!"true".equals(value) && !"false".equals(value)) {
        problem(node, "the default of a boolean field is true or false");
      }
    } else if (type == FieldType.DATE) {
      try {
        LocalDate.parse(value);
      } catch (DateTimeParseException e) {
        problem(node, "the default of a date field must be a date like 2026-01-31");
      }
    } else if (type != FieldType.TEXT) {
      problem(node, "only text, number, choice, unit, boolean and date fields can have a default");
    }
    return value;
  }

  // The default of a field with several choices: a non-empty list of its options in the order of
  // the options, kept as a JSON array of text so that no option can be mistaken for a separator.
  private String readChoicesDefault(List<String> options, boolean unique, Node node) {
    if (unique) {
      problem(node, "a unique field cannot have a default");
      return null;
    }
    if (!(node instanceof SequenceNode sequence) || sequence.getValue().isEmpty()) {
      problem(node, "the default of a field with several choices is a non-empty list of options");
      return null;
    }
    Set<String> chosen = new LinkedHashSet<>();
    for (Node item : sequence.getValue()) {
      String value = scalar(item);
      if (!options.contains(value)) {
        problem(item, "the default must be options of the field");
      } else if (!chosen.add(value)) {
        problem(item, "option " + value + " is listed twice in the default");
      }
    }
    StringBuilder json = new StringBuilder("[");
    for (String value : options.stream().filter(chosen::contains).toList()) {
      json.append(json.length() > 1 ? "," : "")
          .append('"')
          .append(
              value
                  .replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t"))
          .append('"');
    }
    return json.append(']').toString();
  }

  // The ui block of an entity: the icon, the menu entry, the columns of the list, the groups of the
  // form and the hints. It only chooses among the fields the entity has and changes no access.
  private Ui readUi(Node node, Map<String, Field> fields, boolean hasProcess) {
    if (node == null) {
      return null;
    }
    if (!(node instanceof MappingNode map)) {
      problem(node, "ui is a mapping with icon, menu, list, form or hints");
      return null;
    }
    Map<String, Node> keys = mapping(map, Set.of("icon", "menu", "list", "form", "hints"));
    String icon = null;
    Node iconNode = keys.get("icon");
    if (iconNode != null) {
      icon = scalar(iconNode);
      if (!Ui.ICONS.contains(icon)) {
        problem(iconNode, "unknown icon " + icon + ", use " + String.join(", ", Ui.ICONS));
        icon = null;
      }
    }
    Set<String> columns = new LinkedHashSet<>(fields.keySet());
    Ui.LIST_COLUMNS.stream()
        .filter(column -> hasProcess || column.endsWith("_at"))
        .forEach(columns::add);
    return new Ui(
        icon,
        readMenu(keys.get("menu")),
        readListView(keys.get("list"), columns),
        readFormView(keys.get("form"), fields),
        readHints(keys.get("hints"), fields));
  }

  private Menu readMenu(Node node) {
    if (node == null) {
      return null;
    }
    if (!(node instanceof MappingNode map)) {
      problem(node, "menu is a mapping with group and order");
      return null;
    }
    Map<String, Node> keys = mapping(map, Set.of("group", "order"));
    String group = null;
    if (keys.get("group") != null) {
      group = scalar(keys.get("group"));
      if (group.isBlank() || group.length() > 40) {
        problem(keys.get("group"), "the menu group is text of 1 to 40 characters");
        group = null;
      }
    }
    int order = 0;
    if (keys.get("order") != null) {
      String text = scalar(keys.get("order"));
      if (text.matches("[0-9]{1,4}")) {
        order = Integer.parseInt(text);
      } else {
        problem(keys.get("order"), "the menu order is a whole number from 0 to 9999");
      }
    }
    return new Menu(group, order);
  }

  private ListView readListView(Node node, Set<String> columns) {
    if (node == null) {
      return null;
    }
    if (!(node instanceof MappingNode map)) {
      problem(node, "list is a mapping with columns and sort");
      return null;
    }
    Map<String, Node> keys = mapping(map, Set.of("columns", "sort"));
    List<String> shown = new ArrayList<>();
    Node columnsNode = keys.get("columns");
    if (columnsNode != null) {
      if (!(columnsNode instanceof SequenceNode sequence) || sequence.getValue().isEmpty()) {
        problem(columnsNode, "columns is a non-empty list of field names");
      } else {
        for (Node item : sequence.getValue()) {
          String column = scalar(item);
          if (!columns.contains(column)) {
            problem(
                item,
                "column "
                    + column
                    + " is not a field of this entity, use: "
                    + String.join(", ", columns));
          } else if (shown.contains(column)) {
            problem(item, "column " + column + " is listed twice");
          } else {
            shown.add(column);
          }
        }
      }
    }
    String sort = null;
    Node sortNode = keys.get("sort");
    if (sortNode != null) {
      String text = scalar(sortNode);
      String column = text.startsWith("-") ? text.substring(1) : text;
      if (columns.contains(column)) {
        sort = text;
      } else {
        problem(
            sortNode,
            "sort is a column name, with a leading minus for descending, use: "
                + String.join(", ", columns));
      }
    }
    return new ListView(List.copyOf(shown), sort);
  }

  private FormView readFormView(Node node, Map<String, Field> fields) {
    if (node == null) {
      return null;
    }
    if (!(node instanceof MappingNode map)) {
      problem(node, "form is a mapping with groups");
      return null;
    }
    Map<String, Node> keys = mapping(map, Set.of("groups"));
    Node groupsNode = keys.get("groups");
    if (!(groupsNode instanceof SequenceNode sequence) || sequence.getValue().isEmpty()) {
      problem(
          groupsNode == null ? node : groupsNode,
          "groups is a non-empty list of groups with a label and fields");
      return null;
    }
    List<FormGroup> groups = new ArrayList<>();
    Set<String> placed = new HashSet<>();
    for (Node item : sequence.getValue()) {
      if (!(item instanceof MappingNode group)) {
        problem(item, "a group is a mapping with label and fields");
        continue;
      }
      Map<String, Node> parts = mapping(group, Set.of("label", "fields"));
      String label = parts.get("label") == null ? "" : scalar(parts.get("label"));
      if (label.isBlank() || label.length() > 60) {
        problem(
            parts.get("label") == null ? item : parts.get("label"),
            "a group has a label of 1 to 60 characters");
      }
      List<String> names = new ArrayList<>();
      Node fieldsNode = parts.get("fields");
      if (!(fieldsNode instanceof SequenceNode list) || list.getValue().isEmpty()) {
        problem(
            fieldsNode == null ? item : fieldsNode, "a group has a non-empty list of field names");
        continue;
      }
      for (Node field : list.getValue()) {
        String name = scalar(field);
        if (!fields.containsKey(name)) {
          problem(field, "field " + name + " is not a field of this entity");
        } else if (fields.get(name).type() == FieldType.CODE) {
          problem(field, "field " + name + " is a code the system assigns, so it has no input");
        } else if (!placed.add(name)) {
          problem(field, "field " + name + " is in two groups");
        } else {
          names.add(name);
        }
      }
      groups.add(new FormGroup(label, List.copyOf(names)));
    }
    return new FormView(List.copyOf(groups));
  }

  private Map<String, String> readHints(Node node, Map<String, Field> fields) {
    Map<String, String> hints = new LinkedHashMap<>();
    if (node == null) {
      return hints;
    }
    if (!(node instanceof MappingNode map)) {
      problem(node, "hints maps a field name to a line of help");
      return hints;
    }
    for (NodeTuple tuple : map.getValue()) {
      String name = scalar(tuple.getKeyNode());
      String text = scalar(tuple.getValueNode());
      if (!fields.containsKey(name)) {
        problem(tuple.getKeyNode(), "field " + name + " is not a field of this entity");
      } else if (text.isBlank() || text.length() > 200) {
        problem(tuple.getValueNode(), "a hint is text of 1 to 200 characters");
      } else {
        hints.put(name, text);
      }
    }
    return hints;
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
        problem(
            tuple.getValueNode(),
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
    Condition when;
    String check;
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
      draft.when = action.when();
      draft.check = action.check();
      return draft;
    }
  }

  private Process readProcess(
      Node node, Set<String> roleNames, Map<String, Scope> access, Map<String, Field> fields) {
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
      problem(
          familyNode,
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
    Duration deadline = deadline(keys.get("deadline"));
    return new Process(
        family,
        handlers,
        flag(keys.get("allow_self_decision"), "allow_self_decision"),
        deadline,
        readEscalations(keys.get("escalate"), deadline, roleNames, access),
        autoClose.after(),
        autoClose.action(),
        readAfter(keys.get("after"), definition, fields),
        readBooking(map, keys.get("booking"), family, fields),
        definition);
  }

  // One role or a list of roles, each declared in roles and able to read the records of the entity.
  private List<String> readRoles(
      Node parent,
      Node node,
      String key,
      String what,
      Set<String> roleNames,
      Map<String, Scope> access) {
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
        problem(
            item,
            node instanceof SequenceNode
                ? "role "
                    + role
                    + " in "
                    + key
                    + " is not declared in roles, declared: "
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
  private ProcessFamily readDefinition(
      MappingNode process,
      Map<String, Node> keys,
      ProcessFamily template,
      Set<String> roleNames,
      Map<String, Scope> access,
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
    readActions(
        process, keys.get("actions"), template != null, actions, nodes, roleNames, access, fields);
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
        (name, draft) ->
            built.add(
                new Action(
                    name,
                    draft.label,
                    List.copyOf(draft.from),
                    draft.to,
                    draft.by,
                    draft.needsNote,
                    List.copyOf(draft.roles),
                    List.copyOf(draft.require),
                    draft.when,
                    draft.check)));
    List<State> builtStates = new ArrayList<>(states.values());
    if (actions.containsKey(ProcessFamily.WITHDRAW)) {
      builtStates.add(ProcessFamily.WITHDRAWN);
    }
    return new ProcessFamily(
        template == null ? "custom" : template.name(),
        initial,
        List.copyOf(builtStates),
        List.copyOf(built));
  }

  private void readStates(
      MappingNode process,
      Node node,
      boolean fromFamily,
      Map<String, State> states,
      Map<String, Node> nodes) {
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
        problem(
            tuple.getValueNode(),
            "a state is a mapping with label and terminal, or null to remove it");
        continue;
      }
      Map<String, Node> parts = mapping(spec, ProcessSyntax.STATE);
      State existing = states.get(name);
      String label =
          parts.get("label") != null
              ? scalar(parts.get("label"))
              : existing != null ? existing.label() : name;
      boolean terminal =
          parts.get("terminal") != null
              ? flag(parts.get("terminal"), "terminal")
              : existing != null && existing.terminal();
      states.put(name, new State(name, label, terminal));
      nodes.put("state:" + name, keyNode);
    }
  }

  private void readActions(
      MappingNode process,
      Node node,
      boolean fromFamily,
      Map<String, ActionDraft> actions,
      Map<String, Node> nodes,
      Set<String> roleNames,
      Map<String, Scope> access,
      Map<String, Field> fields) {
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
      if (ProcessSyntax.HISTORY_NAMES.contains(name)) {
        problem(
            keyNode,
            "action name "
                + name
                + " is used by the history of a record, choose "
                + "another name");
        continue;
      }
      if (isNull(tuple.getValueNode())) {
        if (actions.remove(name) == null) {
          problem(keyNode, "there is no action " + name + " to remove");
        }
        continue;
      }
      if (!(tuple.getValueNode() instanceof MappingNode spec)) {
        problem(
            tuple.getValueNode(), "an action is a mapping with from and to, or null to remove it");
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

  private boolean readAction(
      String name,
      MappingNode spec,
      ActionDraft draft,
      boolean added,
      Set<String> roleNames,
      Map<String, Scope> access,
      Map<String, Field> fields) {
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
        problem(
            spec,
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
          List<String> roles =
              readRoles(
                  spec, parts.get("role"), "role", "role of action " + name, roleNames, access);
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
    if (parts.get("when") != null) {
      if (isNull(parts.get("when"))) {
        draft.when = null;
      } else if (withdraw || draft.by != Actor.HANDLER) {
        problem(parts.get("when"), "when only applies to an action of the handler");
      } else {
        draft.when = readCondition(parts.get("when"), fields, "when");
      }
    }
    if (parts.get("check") != null) {
      if (isNull(parts.get("check"))) {
        draft.check = null;
      } else {
        draft.check = readName(spec, parts.get("check"), "check");
      }
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
        problem(
            item,
            "require must name fields of this entity that are not files, fields: "
                + fields.values().stream()
                    .filter(f -> f.type() != FieldType.FILE)
                    .map(Field::name)
                    .collect(java.util.stream.Collectors.joining(", ")));
      } else if (!names.contains(value)) {
        names.add(value);
      }
    }
    return names;
  }

  private static final Pattern CONDITION =
      Pattern.compile("^\\s*([a-z][a-z0-9_]*)\\s*(==|!=|<=|>=|<|>)\\s*(.+?)\\s*$");

  // One comparison of a field with a value, written as in "amount > 5000000".
  private Condition readCondition(Node node, Map<String, Field> fields, String key) {
    String example = key + " is one comparison like amount > 5000000, with ==, !=, <, <=, > or >=";
    if (!(node instanceof ScalarNode text)) {
      problem(node, example);
      return null;
    }
    Matcher match = CONDITION.matcher(text.getValue());
    if (!match.matches()) {
      problem(node, example);
      return null;
    }
    Field field = fields.get(match.group(1));
    String op = match.group(2);
    String value = match.group(3);
    if (value.length() > 1
        && (value.startsWith("\"") && value.endsWith("\"")
            || value.startsWith("'") && value.endsWith("'"))) {
      value = value.substring(1, value.length() - 1);
    }
    if (field == null || !comparable(field)) {
      problem(
          node,
          key
              + " must name a number, date, choice, text or boolean field of this entity, fields: "
              + fields.values().stream()
                  .filter(ModelParser::comparable)
                  .map(Field::name)
                  .collect(java.util.stream.Collectors.joining(", ")));
      return null;
    }
    if (Condition.ordering(op)
        && field.type() != FieldType.NUMBER
        && field.type() != FieldType.DATE) {
      problem(
          node,
          key
              + " compares "
              + field.name()
              + " with "
              + op
              + ", which only works on a number or date field");
      return null;
    }
    if (field.type() == FieldType.DATE) {
      try {
        LocalDate.parse(value);
      } catch (DateTimeParseException notDate) {
        problem(
            node,
            key
                + " compares the date field "
                + field.name()
                + " with '"
                + value
                + "', which is not a date like 2026-01-31");
        return null;
      }
    }
    if (field.type() == FieldType.BOOLEAN && !"true".equals(value) && !"false".equals(value)) {
      problem(
          node,
          key
              + " compares the boolean field "
              + field.name()
              + " with '"
              + value
              + "', which is not true or false");
      return null;
    }
    if (field.type() == FieldType.NUMBER) {
      try {
        return new Condition(field.name(), op, new java.math.BigDecimal(value));
      } catch (NumberFormatException notNumber) {
        problem(
            node,
            key
                + " compares the number field "
                + field.name()
                + " with '"
                + value
                + "', which is not a number");
        return null;
      }
    }
    if (field.type() == FieldType.CHOICE && !field.options().contains(value)) {
      problem(
          node,
          key
              + " compares "
              + field.name()
              + " with '"
              + value
              + "', which is not one of its options: "
              + String.join(", ", field.options()));
      return null;
    }
    return new Condition(field.name(), op, value);
  }

  // The fields a condition can compare: a number, a date, a text, a choice of one value or a
  // boolean.
  private static boolean comparable(Field field) {
    return !field.multiple()
        && (field.type() == FieldType.NUMBER
            || field.type() == FieldType.DATE
            || field.type() == FieldType.CHOICE
            || field.type() == FieldType.TEXT
            || field.type() == FieldType.BOOLEAN);
  }

  // The rules that make a set of states and actions a process a record can run through.
  private void checkDefinition(
      MappingNode process,
      Node initialNode,
      String initial,
      Map<String, State> states,
      Map<String, ActionDraft> actions,
      Map<String, Node> nodes) {
    int before = problems.size();
    if (states.isEmpty()) {
      problem(process, "a process needs at least one state");
      return;
    }
    State start = states.get(initial);
    if (start == null) {
      problem(
          initialNode == null ? process : initialNode,
          "initial must be one of the states: " + String.join(", ", states.keySet()));
    } else if (start.terminal()) {
      problem(initialNode == null ? process : initialNode, "the initial state cannot be final");
    }
    if (states.values().stream().noneMatch(State::terminal)) {
      problem(process, "a process needs at least one final state, with terminal: true");
    }
    Set<String> leaving = new HashSet<>();
    Set<String> leavingFreely = new HashSet<>();
    for (Map.Entry<String, ActionDraft> entry : actions.entrySet()) {
      ActionDraft draft = entry.getValue();
      Node at = draft.node == null ? process : draft.node;
      for (String from : draft.from) {
        State state = states.get(from);
        if (state == null) {
          problem(
              draft.fromNode == null ? at : draft.fromNode,
              "action "
                  + entry.getKey()
                  + " leaves "
                  + from
                  + ", which is not a state, states: "
                  + String.join(", ", states.keySet()));
        } else if (state.terminal()) {
          problem(
              draft.fromNode == null ? at : draft.fromNode,
              "action " + entry.getKey() + " leaves the final state " + from);
        } else if (!ProcessFamily.WITHDRAW.equals(entry.getKey())) {
          leaving.add(from);
          if (draft.when == null) {
            leavingFreely.add(from);
          }
        }
      }
      if (!ProcessFamily.WITHDRAW.equals(entry.getKey()) && !states.containsKey(draft.to)) {
        problem(
            draft.toNode == null ? at : draft.toNode,
            "action "
                + entry.getKey()
                + " ends in "
                + draft.to
                + ", which is not a state, states: "
                + String.join(", ", states.keySet()));
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
        problem(
            nodes.getOrDefault("state:" + state.name(), process),
            "state " + state.name() + " has no action leaving it, so a record would stay there");
      } else if (!state.terminal() && !leavingFreely.contains(state.name())) {
        problem(
            nodes.getOrDefault("state:" + state.name(), process),
            "state "
                + state.name()
                + " needs an action leaving it without when, or a record could stay there");
      }
    }
    Set<String> reached = new HashSet<>(Set.of(initial));
    boolean grown = true;
    while (grown) {
      grown = false;
      for (Map.Entry<String, ActionDraft> entry : actions.entrySet()) {
        ActionDraft draft = entry.getValue();
        if (!ProcessFamily.WITHDRAW.equals(entry.getKey())
            && draft.from.stream().anyMatch(reached::contains)
            && reached.add(draft.to)) {
          grown = true;
        }
      }
    }
    for (State state : states.values()) {
      if (!reached.contains(state.name())) {
        problem(
            nodes.getOrDefault("state:" + state.name(), process),
            "state " + state.name() + " can never be reached from " + initial);
      }
    }
  }

  private static boolean isNull(Node node) {
    return node instanceof ScalarNode scalar && Tag.NULL.equals(scalar.getTag());
  }

  private Booking readBooking(
      MappingNode process, Node node, String family, Map<String, Field> fields) {
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
    return resource == null || starts == null || ends == null
        ? null
        : new Booking(resource, starts, ends, capacity);
  }

  private String bookingField(
      MappingNode booking, Node node, String key, FieldType type, Map<String, Field> fields) {
    String name = node == null ? null : scalar(node);
    Field field = name == null ? null : fields.get(name);
    if (field == null || field.type() != type) {
      problem(
          node == null ? booking : node,
          key + " must name a " + type.key() + " field of this entity");
      return null;
    }
    return name;
  }

  private Map<String, List<Completion>> readAfter(
      Node node, ProcessFamily family, Map<String, Field> fields) {
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
      problem(
          doNode == null ? map : doNode,
          "unknown action, available: "
              + String.join(", ", Completion.OPERATIONS.stream().sorted().toList()));
      return null;
    }
    Node userNode = keys.get("user");
    String user = userNode == null ? null : scalar(userNode);
    Field userField = user == null ? null : fields.get(user);
    if (user == null
        || (!Completion.CREATOR.equals(user)
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
    return new Completion(
        operation, user, groupNode == null ? null : scalar(groupNode), groupField);
  }

  // A number followed by m (minutes), h (hours) or d (days), such as 90m, 72h or 3d.
  private Duration deadline(Node node) {
    return duration(node, "deadline", "72h");
  }

  // The notices sent to other roles while an overdue record stays unhandled: a list of levels,
  // each with the time past the deadline, the roles to tell and how often to repeat.
  private List<Escalation> readEscalations(
      Node node, Duration deadline, Set<String> roleNames, Map<String, Scope> access) {
    if (node == null) {
      return List.of();
    }
    if (!(node instanceof SequenceNode sequence) || sequence.getValue().isEmpty()) {
      problem(node, "escalate is a list of {after: 24h, to: MANAGER}");
      return List.of();
    }
    if (deadline == null) {
      problem(node, "escalate counts from the deadline, so the process needs a deadline");
      return List.of();
    }
    if (sequence.getValue().size() > ProcessSyntax.MAX_ESCALATIONS) {
      problem(node, "escalate has at most " + ProcessSyntax.MAX_ESCALATIONS + " levels");
      return List.of();
    }
    List<Escalation> levels = new ArrayList<>();
    for (Node item : sequence.getValue()) {
      if (!(item instanceof MappingNode map)) {
        problem(item, "a level of escalate is {after: 24h, to: MANAGER}");
        continue;
      }
      Map<String, Node> keys = mapping(map, ProcessSyntax.ESCALATION);
      if (keys.get("after") == null) {
        problem(map, "an escalation level needs after, the time past the deadline");
      }
      Duration after =
          keys.get("after") == null ? null : duration(keys.get("after"), "after", "24h");
      List<String> to = readRoles(map, keys.get("to"), "to", "escalation role", roleNames, access);
      int before = problems.size();
      Duration every = null;
      int times = 1;
      if (keys.get("repeat") != null) {
        if (keys.get("repeat") instanceof MappingNode repeat) {
          Map<String, Node> parts = mapping(repeat, ProcessSyntax.REPEAT);
          every = duration(parts.get("every"), "every", "12h");
          times = repeatTimes(repeat, parts.get("times"));
          if (parts.get("every") == null) {
            problem(repeat, "repeat needs every, the time between two notices");
          }
        } else {
          problem(keys.get("repeat"), "repeat is {every: 12h, times: 3}");
        }
      }
      if (after != null && to != null && problems.size() == before) {
        levels.add(new Escalation(after, to, times > 1 ? every : null, times));
      }
    }
    return levels.size() == sequence.getValue().size() ? List.copyOf(levels) : List.of();
  }

  private int repeatTimes(Node repeat, Node node) {
    if (node == null) {
      problem(repeat, "repeat needs times, how many notices a level sends");
      return 1;
    }
    String text = scalar(node);
    try {
      int times = Integer.parseInt(text);
      if (times >= 1 && times <= ProcessSyntax.MAX_REPEATS) {
        return times;
      }
    } catch (NumberFormatException e) {
      // reported below
    }
    problem(node, "times is a whole number from 1 to " + ProcessSyntax.MAX_REPEATS);
    return 1;
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
      problem(
          node, "auto_close needs an action here, as auto_close: {after: 7d, action: <action>}");
      return new AutoClose(null, null);
    }
    return checkedAutoClose(after, action, node, definition);
  }

  private AutoClose checkedAutoClose(
      Duration after, String action, Node node, ProcessFamily definition) {
    Action found = definition.action(action).orElse(null);
    if (found == null || found.by() != Actor.OWNER || ProcessFamily.WITHDRAW.equals(action)) {
      problem(node, "the action of auto_close must be an action of the owner other than withdraw");
      return new AutoClose(null, null);
    }
    if (found.needsNote()) {
      problem(node, "the action of auto_close cannot need a note, as the system writes none");
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
        problem(
            tuple.getKeyNode(),
            "unknown key "
                + key
                + ", allowed: "
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
    problems.add(
        new Problem(
            node.getStartMark().getLine() + 1, node.getStartMark().getColumn() + 1, message));
  }
}
