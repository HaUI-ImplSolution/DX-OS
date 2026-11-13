// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import HaUI.ImplSolution.dxos.engine.apply.Sql;
import HaUI.ImplSolution.dxos.model.CodeFormat;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Gives the records of an entity their readable codes, such as HS-2026-00007. The number comes from
// a counter row that is locked until the record is saved, so two records never share a code and a
// record that fails to save leaves no gap.
@Service
public class RecordCodes {

  private static final String ADVANCE =
      "INSERT INTO engine.record_counter (model, entity, field, period, last_value)"
          + " VALUES (:model, :entity, :field, :period, :by)"
          + " ON CONFLICT (model, entity, field, period)"
          + " DO UPDATE SET last_value = engine.record_counter.last_value + :by"
          + " RETURNING last_value";

  private final NamedParameterJdbcTemplate jdbc;
  private final Clock clock;

  public RecordCodes(NamedParameterJdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  // The code of the record being created now; it runs in the transaction that saves the record.
  @Transactional(propagation = Propagation.MANDATORY)
  public String next(Model model, Entity entity, Field field) {
    CodeFormat format = CodeFormat.parse(field.format());
    int year = LocalDate.now(clock).getYear();
    String period = format.period(year);
    Long number =
        jdbc.queryForObject(
            ADVANCE, counter(model, entity, field, period).addValue("by", 1), Long.class);
    long sequence = number == null ? 1 : number;
    require(format, field, period, sequence);
    return format.render(year, sequence);
  }

  // Gives a code to every record of the entity that has none, oldest first, counting in the year
  // each record was created in. It is safe to run again: a record that has a code is left alone.
  @Transactional
  public int fill(Model model, Entity entity, Field field) {
    CodeFormat format = CodeFormat.parse(field.format());
    String table = Sql.table(model, entity);
    String column = Sql.ident(field.name());
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT id, EXTRACT(YEAR FROM created_at AT TIME ZONE :zone)::int AS year FROM "
                + table
                + " WHERE "
                + column
                + " IS NULL ORDER BY id",
            new MapSqlParameterSource("zone", BusinessTime.ZONE_ID));
    Map<String, List<Map<String, Object>>> byPeriod = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      int year = ((Number) row.get("year")).intValue();
      byPeriod.computeIfAbsent(format.period(year), p -> new ArrayList<>()).add(row);
    }
    for (Map.Entry<String, List<Map<String, Object>>> group : byPeriod.entrySet()) {
      List<Map<String, Object>> batch = group.getValue();
      Long number =
          jdbc.queryForObject(
              ADVANCE,
              counter(model, entity, field, group.getKey()).addValue("by", batch.size()),
              Long.class);
      long first = (number == null ? batch.size() : number) - batch.size() + 1;
      require(format, field, group.getKey(), first + batch.size() - 1);
      MapSqlParameterSource[] updates = new MapSqlParameterSource[batch.size()];
      for (int i = 0; i < batch.size(); i++) {
        updates[i] =
            new MapSqlParameterSource("id", batch.get(i).get("id"))
                .addValue(
                    "code",
                    format.render(((Number) batch.get(i).get("year")).intValue(), first + i));
      }
      jdbc.batchUpdate(
          "UPDATE "
              + table
              + " SET "
              + column
              + " = :code WHERE id = :id AND "
              + column
              + " IS NULL",
          updates);
    }
    return rows.size();
  }

  private static MapSqlParameterSource counter(
      Model model, Entity entity, Field field, String period) {
    return new MapSqlParameterSource("model", model.name())
        .addValue("entity", entity.name())
        .addValue("field", field.name())
        .addValue("period", period);
  }

  private static void require(CodeFormat format, Field field, String period, long sequence) {
    if (sequence > format.last()) {
      throw new PokaYokeViolationException(
          "ERR_CODE_EXHAUSTED",
          "The codes of "
              + field.name()
              + " are used up for "
              + ("-".equals(period) ? "all time" : period)
              + "; widen {seq:N} in the format");
    }
  }
}
