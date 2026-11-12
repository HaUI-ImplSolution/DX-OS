// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Measures the quality of the records of an entity that the caller can see, with every
// QualityMetric bean, over the same rows and filters as the list.
@Service
public class DataQualityService {

  static final int DEFAULT_STALE_DAYS = 90;
  static final int MAX_STALE_DAYS = 3650;
  private static final int TIMEOUT_MS = 10_000;

  private final DataService data;
  private final NamedParameterJdbcTemplate jdbc;
  private final List<QualityMetric> metrics;

  public DataQualityService(
      DataService data, NamedParameterJdbcTemplate jdbc, List<QualityMetric> metrics) {
    this.data = data;
    this.jdbc = jdbc;
    this.metrics = metrics;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> quality(Model model, Entity entity, Map<String, String> params) {
    int staleDays = staleDays(params);
    DataService.Rows rows = data.rows(model, entity, params);
    // Applies to this transaction only: the measures stop after the limit.
    jdbc.getJdbcOperations().execute("SET LOCAL statement_timeout = " + TIMEOUT_MS);
    OffsetDateTime at = OffsetDateTime.now();
    Long size = jdbc.queryForObject("SELECT count(*)" + rows.from(), rows.values(), Long.class);
    QualityMetric.Sample sample =
        new QualityMetric.Sample(
            entity, rows.from(), rows.values(), size == null ? 0 : size, at, staleDays);
    Map<String, Object> measured = new LinkedHashMap<>();
    for (QualityMetric metric : metrics) {
      measured.put(metric.key(), metric.measure(jdbc, sample));
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model.name());
    body.put("entity", entity.name());
    body.put("measuredAt", at.toInstant().toString());
    body.put("sampleSize", sample.size());
    body.put("metrics", measured);
    return body;
  }

  private static int staleDays(Map<String, String> params) {
    String raw = params.get("staleDays");
    if (raw == null || raw.isBlank()) {
      return DEFAULT_STALE_DAYS;
    }
    try {
      int days = Integer.parseInt(raw.trim());
      if (days >= 1 && days <= MAX_STALE_DAYS) {
        return days;
      }
    } catch (NumberFormatException e) {
      // falls through to the same answer as a number out of range
    }
    throw new ModelValidationException(
        "staleDays", "must be a whole number from 1 to " + MAX_STALE_DAYS);
  }
}
