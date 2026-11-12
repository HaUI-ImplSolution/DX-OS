// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import static HaUI.ImplSolution.dxos.engine.apply.Sql.ident;

import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

// One measure of the quality of the records of an entity. Every metric that is a bean is run by
// DataQualityService and reported under its key, so a new measure is a new class and nothing else.
// A metric reads only the rows of the sample and names columns from the model, never a domain.
interface QualityMetric {

  // The rows the caller can see: from is the tail of a query that selects them and values holds
  // its parameters, size is how many there are, at is the moment of the measure.
  record Sample(
      Entity entity,
      String from,
      MapSqlParameterSource values,
      long size,
      OffsetDateTime at,
      int staleDays) {}

  String key();

  Object measure(NamedParameterJdbcTemplate jdbc, Sample sample);

  // Whether a column holds a value: text and lists count when they are not empty.
  static String filled(Field field) {
    String column = ident(field.name());
    if (field.multiple()) {
      return "COALESCE(cardinality(" + column + ") > 0, FALSE)";
    }
    return switch (field.type()) {
      case TEXT, CHOICE, USER, UNIT, FILE -> "COALESCE(btrim(" + column + ") <> '', FALSE)";
      default -> column + " IS NOT NULL";
    };
  }

  // part over size to four places; null when there is no row to measure.
  static Double rate(long part, long size) {
    return size == 0 ? null : Math.round(part * 10_000.0 / size) / 10_000.0;
  }

  static String instant(Object value) {
    if (value instanceof Timestamp timestamp) {
      return timestamp.toInstant().toString();
    }
    if (value instanceof OffsetDateTime time) {
      return time.toInstant().toString();
    }
    return null;
  }
}
