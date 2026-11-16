// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import HaUI.ImplSolution.dxos.model.ModelException.Problem;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.resolver.Resolver;
import org.yaml.snakeyaml.serializer.Serializer;

// A model file that starts from a template: the template with the file laid over it.
//
// Mappings merge key by key, and a key set to null removes the template's key.
// The top-level lists roles and units merge by name; every other list is replaced whole.
// The merge works on YAML nodes, so scalars keep their text and type.
public final class ModelExtends {

  private static final String EXTENDS = "extends";
  private static final String FORMAT = "format";
  private static final String NAME = "name";
  private static final Set<String> BY_NAME = Set.of("roles", "units");

  private ModelExtends() {}

  // The template a file extends, or empty when it extends none or is not a readable mapping,
  // which the model reader reports in its own words.
  public static Optional<String> templateOf(String fileYaml) {
    List<Problem> problems = new ArrayList<>();
    MappingNode top = top(fileYaml, "the file", problems);
    if (top == null) {
      return Optional.empty();
    }
    Node node = value(top, EXTENDS);
    if (node == null) {
      return Optional.empty();
    }
    String id = extendsId(node, problems);
    if (!problems.isEmpty()) {
      throw new ModelException(problems);
    }
    return Optional.of(id);
  }

  // The file laid over the template, as a model file without the extends key.
  public static String merge(String templateYaml, String fileYaml) {
    List<Problem> problems = new ArrayList<>();
    MappingNode file = top(fileYaml, "the file", problems);
    if (file == null) {
      throw new ModelException(problems);
    }
    MappingNode template = top(templateYaml, "the template", new ArrayList<>());
    if (template == null) {
      throw new ModelException(
          List.of(new Problem(0, 0, "the template is not a model file that can be read")));
    }

    Node extendsNode = value(file, EXTENDS);
    if (extendsNode != null) {
      extendsId(extendsNode, problems);
    }
    if (value(file, NAME) == null) {
      problems.add(at(file, "name is required in a file that extends a template"));
    }
    Node fileFormat = value(file, FORMAT);
    Node templateFormat = value(template, FORMAT);
    if (fileFormat != null
        && templateFormat != null
        && !text(fileFormat).equals(text(templateFormat))) {
      problems.add(
          at(
              fileFormat,
              "format "
                  + text(fileFormat)
                  + " differs from the template's "
                  + text(templateFormat)));
    }
    if (!problems.isEmpty()) {
      throw new ModelException(problems);
    }

    List<NodeTuple> merged = new ArrayList<>();
    Map<String, NodeTuple> fileKeys = keyed(file, Set.of(EXTENDS, FORMAT));
    for (NodeTuple tuple : template.getValue()) {
      String key = text(tuple.getKeyNode());
      NodeTuple over = fileKeys.remove(key);
      if (over == null) {
        merged.add(tuple);
        continue;
      }
      Node value = mergeValue(key, tuple.getValueNode(), over.getValueNode(), true);
      if (value != null) {
        merged.add(new NodeTuple(tuple.getKeyNode(), value));
      }
    }
    for (NodeTuple tuple : fileKeys.values()) {
      if (!isNull(tuple.getValueNode())) {
        merged.add(tuple);
      }
    }
    return dump(new MappingNode(Tag.MAP, merged, template.getFlowStyle()));
  }

  private static Node mergeValue(String key, Node template, Node file, boolean topLevel) {
    if (isNull(file)) {
      return null;
    }
    if (template instanceof MappingNode t && file instanceof MappingNode f) {
      return mergeMapping(t, f);
    }
    if (topLevel
        && BY_NAME.contains(key)
        && template instanceof SequenceNode t
        && file instanceof SequenceNode f) {
      return mergeByName(t, f);
    }
    return file;
  }

  private static MappingNode mergeMapping(MappingNode template, MappingNode file) {
    List<NodeTuple> merged = new ArrayList<>();
    Map<String, NodeTuple> fileKeys = keyed(file, Set.of());
    for (NodeTuple tuple : template.getValue()) {
      NodeTuple over = fileKeys.remove(text(tuple.getKeyNode()));
      if (over == null) {
        merged.add(tuple);
        continue;
      }
      Node value =
          mergeValue(text(tuple.getKeyNode()), tuple.getValueNode(), over.getValueNode(), false);
      if (value != null) {
        merged.add(new NodeTuple(tuple.getKeyNode(), value));
      }
    }
    for (NodeTuple tuple : fileKeys.values()) {
      if (!isNull(tuple.getValueNode())) {
        merged.add(tuple);
      }
    }
    return new MappingNode(template.getTag(), merged, template.getFlowStyle());
  }

  // Items with the same name merge; the file's new items follow the template's.
  private static SequenceNode mergeByName(SequenceNode template, SequenceNode file) {
    List<Node> unmatched = new ArrayList<>(file.getValue());
    List<Node> merged = new ArrayList<>();
    for (Node item : template.getValue()) {
      Node over = null;
      if (item instanceof MappingNode t && value(t, NAME) != null) {
        String name = text(value(t, NAME));
        for (Node candidate : unmatched) {
          if (candidate instanceof MappingNode c
              && value(c, NAME) != null
              && text(value(c, NAME)).equals(name)) {
            over = candidate;
            break;
          }
        }
      }
      if (over == null) {
        merged.add(item);
      } else {
        unmatched.remove(over);
        merged.add(mergeMapping((MappingNode) item, (MappingNode) over));
      }
    }
    merged.addAll(unmatched);
    return new SequenceNode(template.getTag(), merged, template.getFlowStyle());
  }

  private static String extendsId(Node node, List<Problem> problems) {
    if (!(node instanceof ScalarNode scalar)
        || !Tag.STR.equals(scalar.getTag())
        || scalar.getValue().isBlank()) {
      problems.add(at(node, "extends names a template: a text such as notes"));
      return "";
    }
    return scalar.getValue();
  }

  private static MappingNode top(String yaml, String what, List<Problem> problems) {
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
    if (root instanceof MappingNode mapping) {
      repeated(mapping, problems);
      return mapping;
    }
    problems.add(at(root, what + " must be a mapping with a name and the keys to change"));
    return null;
  }

  // A key written twice in one mapping would silently keep only the last.
  private static void repeated(Node node, List<Problem> problems) {
    if (node instanceof MappingNode map) {
      Set<String> seen = new HashSet<>();
      for (NodeTuple tuple : map.getValue()) {
        if (tuple.getKeyNode() instanceof ScalarNode key && !seen.add(key.getValue())) {
          problems.add(at(key, "key " + key.getValue() + " is written twice"));
        }
        repeated(tuple.getValueNode(), problems);
      }
    } else if (node instanceof SequenceNode sequence) {
      sequence.getValue().forEach(item -> repeated(item, problems));
    }
  }

  private static Node value(MappingNode map, String key) {
    for (NodeTuple tuple : map.getValue()) {
      if (key.equals(text(tuple.getKeyNode()))) {
        return tuple.getValueNode();
      }
    }
    return null;
  }

  private static Map<String, NodeTuple> keyed(MappingNode map, Set<String> skip) {
    Map<String, NodeTuple> keyed = new LinkedHashMap<>();
    for (NodeTuple tuple : map.getValue()) {
      String key = text(tuple.getKeyNode());
      if (!skip.contains(key)) {
        keyed.put(key, tuple);
      }
    }
    return keyed;
  }

  private static String text(Node node) {
    return node instanceof ScalarNode scalar ? scalar.getValue() : "";
  }

  private static boolean isNull(Node node) {
    return node instanceof ScalarNode scalar && Tag.NULL.equals(scalar.getTag());
  }

  private static Problem at(Node node, String message) {
    if (node == null || node.getStartMark() == null) {
      return new Problem(0, 0, message);
    }
    return new Problem(
        node.getStartMark().getLine() + 1, node.getStartMark().getColumn() + 1, message);
  }

  private static String dump(Node root) {
    DumperOptions options = new DumperOptions();
    options.setIndent(2);
    options.setIndicatorIndent(2);
    options.setIndentWithIndicator(true);
    options.setWidth(10_000);
    options.setAllowUnicode(true);
    StringWriter out = new StringWriter();
    try {
      Serializer serializer =
          new Serializer(new Emitter(out, options), new Resolver(), options, null);
      serializer.open();
      serializer.serialize(root);
      serializer.close();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return out.toString();
  }
}
