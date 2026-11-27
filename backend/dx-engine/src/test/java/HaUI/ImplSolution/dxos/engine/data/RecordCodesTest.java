// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import HaUI.ImplSolution.dxos.model.ModelParser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

// The counter lives in the database; the tests give the number it would answer and check the code
// that is written from it.
class RecordCodesTest {

  private static Model model(String format) {
    return ModelParser.parse(
        """
        format: 1
        name: shop
        roles:
          - name: A
        entities:
          item:
            fields:
              code: {type: code, format: "%s"}
            access: {A: all}
        """
            .formatted(format));
  }

  private static Clock clock(String instant) {
    return Clock.fixed(Instant.parse(instant), BusinessTime.ZONE);
  }

  private static String next(
      String format, long counter, String instant, MapSqlParameterSource[] seen) {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
        .thenReturn(counter);
    Model model = model(format);
    Entity entity = model.entities().get("item");
    Field field = entity.field("code").get();

    String code = new RecordCodes(jdbc, clock(instant)).next(model, entity, field);

    ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
    verify(jdbc).queryForObject(anyString(), params.capture(), eq(Long.class));
    seen[0] = (MapSqlParameterSource) params.getValue();
    return code;
  }

  @Test
  void aCodeIsWrittenFromTheCounterAndTheYear() {
    MapSqlParameterSource[] seen = new MapSqlParameterSource[1];

    String code = next("HS-{year}-{seq:5}", 7, "2026-06-15T03:00:00Z", seen);

    assertEquals("HS-2026-00007", code);
    assertEquals("2026", seen[0].getValue("period"));
    assertEquals("shop", seen[0].getValue("model"));
    assertEquals("item", seen[0].getValue("entity"));
    assertEquals("code", seen[0].getValue("field"));
    assertEquals(1, seen[0].getValue("by"));
  }

  @Test
  void theYearIsTheYearOfTheBusinessDayNotOfUtc() {
    MapSqlParameterSource[] seen = new MapSqlParameterSource[1];

    // 20:00 UTC on 31 December is already 1 January in Ho Chi Minh City.
    String code = next("HS-{year}-{seq:3}", 1, "2026-12-31T20:00:00Z", seen);

    assertEquals("HS-2027-001", code);
    assertEquals("2027", seen[0].getValue("period"));
  }

  @Test
  void aFormatWithoutAYearUsesOneCounterForAllTime() {
    MapSqlParameterSource[] seen = new MapSqlParameterSource[1];

    String code = next("TU{seq:4}", 42, "2026-06-15T03:00:00Z", seen);

    assertEquals("TU0042", code);
    assertEquals("-", seen[0].getValue("period"));
  }

  @Test
  void theLastNumberTheWidthHoldsIsStillGivenAndTheNextOneIsRefused() {
    MapSqlParameterSource[] seen = new MapSqlParameterSource[1];

    assertEquals("A-99", next("A-{seq:2}", 99, "2026-06-15T03:00:00Z", seen));

    PokaYokeViolationException refused =
        assertThrows(
            PokaYokeViolationException.class,
            () -> next("A-{seq:2}", 100, "2026-06-15T03:00:00Z", seen));
    assertEquals("ERR_CODE_EXHAUSTED", refused.getErrorCode());
    assertEquals(
        "The codes of code are used up for all time; widen {seq:N} in the format",
        refused.getMessage());
  }

  @Test
  void anExhaustedYearNamesTheYear() {
    MapSqlParameterSource[] seen = new MapSqlParameterSource[1];

    PokaYokeViolationException refused =
        assertThrows(
            PokaYokeViolationException.class,
            () -> next("A-{year}-{seq:1}", 10, "2026-06-15T03:00:00Z", seen));

    assertEquals(
        "The codes of code are used up for 2026; widen {seq:N} in the format",
        refused.getMessage());
  }

  @Test
  void recordsWithoutACodeGetOneInTheYearTheyWereCreatedOldestFirst() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForList(anyString(), any(SqlParameterSource.class)))
        .thenReturn(
            List.of(
                Map.of("id", 1L, "year", 2025),
                Map.of("id", 2L, "year", 2026),
                Map.of("id", 3L, "year", 2026)));
    // The counter answers its value after it advanced by the size of each year's batch.
    when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
        .thenReturn(1L, 2L);
    Model model = model("X-{year}-{seq:3}");
    Entity entity = model.entities().get("item");

    int filled =
        new RecordCodes(jdbc, clock("2026-06-15T03:00:00Z"))
            .fill(model, entity, entity.field("code").get());

    assertEquals(3, filled);
    ArgumentCaptor<SqlParameterSource[]> updates =
        ArgumentCaptor.forClass(SqlParameterSource[].class);
    verify(jdbc, org.mockito.Mockito.times(2)).batchUpdate(anyString(), updates.capture());
    assertEquals("X-2025-001", updates.getAllValues().get(0)[0].getValue("code"));
    assertEquals("X-2026-001", updates.getAllValues().get(1)[0].getValue("code"));
    assertEquals("X-2026-002", updates.getAllValues().get(1)[1].getValue("code"));
    assertEquals(3L, updates.getAllValues().get(1)[1].getValue("id"));
  }
}
