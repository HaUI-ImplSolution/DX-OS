// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import static HaUI.ImplSolution.dxos.engine.apply.Sql.ident;
import static HaUI.ImplSolution.dxos.engine.apply.Sql.literal;

import HaUI.ImplSolution.dxos.engine.apply.DdlGenerator;
import HaUI.ImplSolution.dxos.model.Model.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

// Checks the stored values again against the rules the model declares for a field: the choices it
// allows, its lowest and highest value and its pattern. The database enforces the same rules, so a
// count above zero means data that was restored or written outside the application.
@Component
@Order(3)
final class ConstraintMetric implements QualityMetric {

  private record Rule(String name, String violated) {}

  @Override
  public String key() {
    return "constraints";
  }

  @Override
  public Object measure(NamedParameterJdbcTemplate jdbc, Sample sample) {
    List<Field> fields =
        sample.entity().fields().values().stream()
            .filter(field -> !rules(field).isEmpty())
            .toList();
    List<Map<String, Object>> out = new ArrayList<>();
    if (!fields.isEmpty()) {
      String select =
          IntStream.range(0, fields.size())
              .mapToObj(
                  i ->
                      "count(*) FILTER (WHERE "
                          + rules(fields.get(i)).stream()
                              .map(rule -> "(" + rule.violated() + ")")
                              .collect(Collectors.joining(" OR "))
                          + ") AS v"
                          + i)
              .collect(Collectors.joining(", "));
      Map<String, Object> row =
          jdbc.queryForMap("SELECT " + select + sample.from(), sample.values());
      for (int i = 0; i < fields.size(); i++) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("field", fields.get(i).name());
        item.put("rules", rules(fields.get(i)).stream().map(Rule::name).toList());
        item.put("violating", ((Number) row.get("v" + i)).longValue());
        out.add(item);
      }
    }
    return Map.of("fields", out);
  }

  // The rules of a field as the condition a value breaks; a row without a value breaks none.
  private static List<Rule> rules(Field field) {
    String column = ident(field.name());
    List<Rule> rules = new ArrayList<>();
    if (field.options() != null && !field.options().isEmpty()) {
      String values =
          field.options().stream().map(value -> literal(value)).collect(Collectors.joining(", "));
      rules.add(
          new Rule(
              "options",
              field.multiple()
                  ? "NOT (" + column + " <@ ARRAY[" + values + "]::text[])"
                  : column + " NOT IN (" + values + ")"));
    }
    if (field.min() != null) {
      rules.add(new Rule("min", column + " < " + DdlGenerator.bound(field, field.min())));
    }
    if (field.max() != null) {
      rules.add(new Rule("max", column + " > " + DdlGenerator.bound(field, field.max())));
    }
    if (field.pattern() != null) {
      rules.add(
          new Rule("pattern", column + " !~ " + literal(DdlGenerator.anchored(field.pattern()))));
    }
    return rules;
  }
}
