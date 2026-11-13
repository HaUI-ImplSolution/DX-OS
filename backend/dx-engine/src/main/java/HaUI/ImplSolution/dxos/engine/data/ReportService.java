// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import static HaUI.ImplSolution.dxos.engine.apply.Sql.ident;

import HaUI.ImplSolution.dxos.engine.data.ReportQuery.Group;
import HaUI.ImplSolution.dxos.engine.data.ReportQuery.Metric;
import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Groups the records the caller can read by one field and computes counts, sums, means and bounds
// over them. It runs on the same rows and filters as the stats, so a caller never reaches a figure
// of a row the list would not show; the field names come from the model and are checked, and no
// part of the request becomes SQL as written.
@Service
public class ReportService {

  private static final int TIMEOUT_MS = 10_000;

  private final DataService data;
  private final NamedParameterJdbcTemplate jdbc;

  public ReportService(DataService data, NamedParameterJdbcTemplate jdbc) {
    this.data = data;
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> report(Model model, Entity entity, Map<String, String> params) {
    ReportQuery query = ReportQuery.parse(entity, params);
    DataService.Rows rows = data.readableRows(model, entity, params);
    // Applies to this transaction only: the query stops after the limit.
    jdbc.getJdbcOperations().execute("SET LOCAL statement_timeout = " + TIMEOUT_MS);
    return run(query, rows);
  }

  Map<String, Object> run(ReportQuery query, DataService.Rows rows) {
    Map<String, Object> body = new LinkedHashMap<>();
    Group group = query.group();
    body.put("groupBy", group == null ? null : group.label());
    body.put("metrics", query.metrics().stream().map(Metric::label).toList());
    List<Map<String, Object>> groups = new ArrayList<>();
    boolean truncated = false;
    if (group != null) {
      MapSqlParameterSource values = new MapSqlParameterSource(rows.values().getValues());
      values.addValue("report_limit", query.limit() + 1);
      List<Map<String, Object>> found =
          jdbc.queryForList(
              "SELECT "
                  + key(group)
                  + " AS k, "
                  + figures(query)
                  + rows.from()
                  + " GROUP BY 1 ORDER BY 1 NULLS LAST LIMIT :report_limit",
              values);
      truncated = found.size() > query.limit();
      for (Map<String, Object> row : found.subList(0, Math.min(found.size(), query.limit()))) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("key", row.get("k"));
        entry.putAll(read(query, row));
        groups.add(entry);
      }
    }
    body.put("groups", groups);
    body.put("truncated", truncated);
    body.put(
        "totals",
        read(query, jdbc.queryForMap("SELECT " + figures(query) + rows.from(), rows.values())));
    body.put("computedAt", OffsetDateTime.now().toString());
    return body;
  }

  // The value a group is named by: the stored value, or the month as YYYY-MM (in UTC for a moment).
  private static String key(Group group) {
    String column = ident(group.field());
    if (!group.month()) {
      return column;
    }
    String instant = group.type() == FieldType.DATE ? column : column + " AT TIME ZONE 'UTC'";
    return "to_char(date_trunc('month', " + instant + "), 'YYYY-MM')";
  }

  // The figures as columns m0, m1, ...; a sum, mean or bound ignores the empty values and is null
  // when a group holds none, and a mean is rounded to four decimals.
  private static String figures(ReportQuery query) {
    List<String> columns = new ArrayList<>();
    for (int i = 0; i < query.metrics().size(); i++) {
      Metric metric = query.metrics().get(i);
      String expression =
          switch (metric.function()) {
            case "count" -> "count(*)";
            case "avg" -> "round(avg(" + ident(metric.field()) + "), 4)";
            default -> metric.function() + "(" + ident(metric.field()) + ")";
          };
      columns.add(expression + " AS m" + i);
    }
    return String.join(", ", columns);
  }

  private static Map<String, Object> read(ReportQuery query, Map<String, Object> row) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (int i = 0; i < query.metrics().size(); i++) {
      out.put(query.metrics().get(i).label(), row.get("m" + i));
    }
    return out;
  }
}
