// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import HaUI.ImplSolution.dxos.model.ModelException.Problem;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

// Upgrades the format marker one version at a time without changing the rest of the file.
public final class ModelUpgrader {

  public static final int CURRENT_FORMAT = 1;

  private static final Map<Integer, Migration> MIGRATIONS =
      Map.of(0, (content, format) -> replace(format, content, 1));

  private ModelUpgrader() {}

  public record Result(int fromFormat, int toFormat, List<String> migrations, String content) {}

  @FunctionalInterface
  interface Migration {
    String apply(String content, ScalarNode format);
  }

  public static Result upgrade(String yaml) {
    return upgrade(yaml, CURRENT_FORMAT, MIGRATIONS);
  }

  static Result upgrade(String yaml, int target, Map<Integer, Migration> migrations) {
    Format found = format(yaml);
    int from = found.value();
    if (from > target) {
      throw problem(found.node(), "unsupported format " + from + ", latest is " + target);
    }
    String content = yaml;
    List<String> applied = new ArrayList<>();
    for (int current = from; current < target; current++) {
      Format before = format(content);
      Migration migration = migrations.get(current);
      if (migration == null) {
        throw problem(
            before.node(), "no migration from format " + current + " to " + (current + 1));
      }
      content = migration.apply(content, before.node());
      Format after = format(content);
      if (after.value() != current + 1) {
        throw problem(
            after.node(),
            "migration from format " + current + " did not produce format " + (current + 1));
      }
      applied.add(current + " -> " + (current + 1));
    }
    return new Result(from, target, List.copyOf(applied), content);
  }

  private static Format format(String yaml) {
    Node root;
    try {
      LoaderOptions options = new LoaderOptions();
      options.setMaxAliasesForCollections(0);
      options.setAllowDuplicateKeys(false);
      root = new Yaml(options).compose(new StringReader(yaml));
    } catch (MarkedYAMLException e) {
      int line = e.getProblemMark() == null ? 0 : e.getProblemMark().getLine() + 1;
      int column = e.getProblemMark() == null ? 0 : e.getProblemMark().getColumn() + 1;
      throw new ModelException(
          List.of(new Problem(line, column, "invalid YAML: " + e.getProblem())));
    }
    if (!(root instanceof MappingNode mapping)) {
      throw problem(root, "the file must be a mapping with a format");
    }
    Map<String, Node> keys = new LinkedHashMap<>();
    for (NodeTuple tuple : mapping.getValue()) {
      if (tuple.getKeyNode() instanceof ScalarNode key) {
        keys.put(key.getValue(), tuple.getValueNode());
      }
    }
    Node node = keys.get("format");
    if (!(node instanceof ScalarNode scalar)) {
      throw problem(node == null ? root : node, "format must be a non-negative integer");
    }
    try {
      int value = Integer.parseInt(scalar.getValue());
      if (value < 0) {
        throw new NumberFormatException();
      }
      return new Format(value, scalar);
    } catch (NumberFormatException e) {
      throw problem(scalar, "format must be a non-negative integer");
    }
  }

  private static String replace(ScalarNode node, String content, int value) {
    // SnakeYAML marks count Unicode code points; String.substring uses UTF-16 offsets.
    int start = content.offsetByCodePoints(0, node.getStartMark().getIndex());
    int end = content.offsetByCodePoints(0, node.getEndMark().getIndex());
    return content.substring(0, start) + value + content.substring(end);
  }

  private static ModelException problem(Node node, String message) {
    int line = node == null ? 0 : node.getStartMark().getLine() + 1;
    int column = node == null ? 0 : node.getStartMark().getColumn() + 1;
    return new ModelException(List.of(new Problem(line, column, message)));
  }

  private record Format(int value, ScalarNode node) {}
}
