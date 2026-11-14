// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

// When the records were last changed: the oldest and the newest change, and how many records have
// not changed within staleDays days. A record of a process that reached its final state is not
// counted, since nothing is left to do on it.
@Component
@Order(4)
final class FreshnessMetric implements QualityMetric {

  @Override
  public String key() {
    return "freshness";
  }

  @Override
  public Object measure(NamedParameterJdbcTemplate jdbc, Sample sample) {
    sample.values().addValue("cutoff", sample.at().minusDays(sample.staleDays()));
    String open = sample.entity().process() == null ? "" : " AND decided_at IS NULL";
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT min(updated_at) AS oldest, max(updated_at) AS newest,"
                + " count(*) FILTER (WHERE updated_at < :cutoff"
                + open
                + ") AS stale"
                + sample.from(),
            sample.values());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("staleDays", sample.staleDays());
    out.put("oldest", QualityMetric.instant(row.get("oldest")));
    out.put("newest", QualityMetric.instant(row.get("newest")));
    out.put("stale", ((Number) row.get("stale")).longValue());
    return out;
  }
}
