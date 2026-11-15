// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import static HaUI.ImplSolution.dxos.engine.apply.Sql.ident;

import HaUI.ImplSolution.dxos.model.Model.Field;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Condition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

// For each field that is required when another field meets a condition: how many records meet the
// condition and how many of those hold no value. The rule is checked when a record is written, so
// a count above zero means a record reached the database some other way.
@Component
@Order(2)
final class RequiredWhenMetric implements QualityMetric {

  @Override
  public String key() {
    return "requiredWhen";
  }

  @Override
  public Object measure(NamedParameterJdbcTemplate jdbc, Sample sample) {
    List<Field> fields =
        sample.entity().fields().values().stream()
            .filter(field -> field.requiredWhen() != null)
            .toList();
    List<Map<String, Object>> out = new ArrayList<>();
    if (!fields.isEmpty()) {
      String select =
          IntStream.range(0, fields.size())
              .mapToObj(
                  i -> {
                    Condition condition = fields.get(i).requiredWhen();
                    String test = test(condition, sample, "rw" + i);
                    return "count(*) FILTER (WHERE "
                        + test
                        + ") AS a"
                        + i
                        + ", count(*) FILTER (WHERE "
                        + test
                        + " AND NOT "
                        + QualityMetric.filled(fields.get(i))
                        + ") AS m"
                        + i;
                  })
              .collect(Collectors.joining(", "));
      Map<String, Object> row =
          jdbc.queryForMap("SELECT " + select + sample.from(), sample.values());
      for (int i = 0; i < fields.size(); i++) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("field", fields.get(i).name());
        item.put("when", fields.get(i).requiredWhen().text());
        item.put("applicable", ((Number) row.get("a" + i)).longValue());
        item.put("missing", ((Number) row.get("m" + i)).longValue());
        out.add(item);
      }
    }
    return Map.of("fields", out);
  }

  // The condition as SQL, which a row without a value in the field never meets. A number compares
  // as a number, an ordering on anything else as a date, and an equality as text.
  private static String test(Condition condition, Sample sample, String param) {
    String column = ident(condition.field());
    String op =
        switch (condition.op()) {
          case "==" -> "=";
          case "!=" -> "<>";
          default -> condition.op();
        };
    sample.values().addValue(param, condition.value());
    if (condition.value() instanceof BigDecimal) {
      return column + " " + op + " :" + param;
    }
    if (Condition.ordering(condition.op())) {
      return column + " " + op + " CAST(:" + param + " AS date)";
    }
    return "CAST(" + column + " AS text) " + op + " :" + param;
  }
}
