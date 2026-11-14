// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

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

// How many records hold a value in each field. An empty text and an empty list count as missing.
@Component
@Order(1)
final class CompletenessMetric implements QualityMetric {

  @Override
  public String key() {
    return "completeness";
  }

  @Override
  public Object measure(NamedParameterJdbcTemplate jdbc, Sample sample) {
    List<Field> fields = List.copyOf(sample.entity().fields().values());
    List<Map<String, Object>> out = new ArrayList<>();
    if (!fields.isEmpty()) {
      String select =
          IntStream.range(0, fields.size())
              .mapToObj(
                  i ->
                      "count(*) FILTER (WHERE "
                          + QualityMetric.filled(fields.get(i))
                          + ") AS c"
                          + i)
              .collect(Collectors.joining(", "));
      Map<String, Object> row =
          jdbc.queryForMap("SELECT " + select + sample.from(), sample.values());
      for (int i = 0; i < fields.size(); i++) {
        long filled = ((Number) row.get("c" + i)).longValue();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("field", fields.get(i).name());
        item.put("required", fields.get(i).required());
        item.put("filled", filled);
        item.put("missing", sample.size() - filled);
        item.put("rate", QualityMetric.rate(filled, sample.size()));
        out.add(item);
      }
    }
    return Map.of("fields", out);
  }
}
