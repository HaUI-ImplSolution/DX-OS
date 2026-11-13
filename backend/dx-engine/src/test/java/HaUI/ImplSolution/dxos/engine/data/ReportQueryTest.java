// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.ModelParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

// A request is checked against the fields of the entity before any query runs.
class ReportQueryTest {

  private static final String MODEL =
      """
      format: 0
      name: report
      roles:
        - name: STAFF
      units:
        - name: NORTH
          label: North
      entities:
        item:
          fields:
            kind: {type: choice, options: [a, b]}
            tags: {type: choice, multiple: true, options: [x, y]}
            site: {type: unit}
            flag: {type: boolean}
            due: {type: date}
            qty: {type: number}
            note: {type: text}
          access: {STAFF: all}
        ticket:
          fields:
            kind: {type: choice, options: [a, b]}
          access: {STAFF: all}
          process: {family: approval, handler: STAFF}
      """;

  private static final Entity ITEM = ModelParser.parse(MODEL).entity("item").orElseThrow();
  private static final Entity TICKET = ModelParser.parse(MODEL).entity("ticket").orElseThrow();

  private static ReportQuery parse(Entity entity, String... pairs) {
    Map<String, String> params = new java.util.HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      params.put(pairs[i], pairs[i + 1]);
    }
    return ReportQuery.parse(entity, params);
  }

  private static void refused(Entity entity, String name, String value, String reason) {
    assertThatThrownBy(() -> parse(entity, name, value))
        .isInstanceOf(ModelValidationException.class)
        .hasMessageContaining(reason);
  }

  @Test
  void noParametersMeanOneCountOverAllRows() {
    ReportQuery query = parse(ITEM);

    assertThat(query.group()).isNull();
    assertThat(query.metrics()).extracting(ReportQuery.Metric::label).containsExactly("count");
    assertThat(query.limit()).isEqualTo(ReportQuery.DEFAULT_LIMIT);
  }

  @Test
  void aChoiceUnitBooleanOrDateFieldCanBeGroupedBy() {
    assertThat(parse(ITEM, "groupBy", "kind").group().label()).isEqualTo("kind");
    assertThat(parse(ITEM, "groupBy", "site").group().label()).isEqualTo("site");
    assertThat(parse(ITEM, "groupBy", "flag").group().label()).isEqualTo("flag");
    assertThat(parse(ITEM, "groupBy", "due:month").group().month()).isTrue();
    assertThat(parse(ITEM, "groupBy", "created_at:month").group().month()).isTrue();
    assertThat(parse(TICKET, "groupBy", "status").group().field()).isEqualTo("status");
  }

  @Test
  void otherFieldsAndOddNamesAreRefused() {
    refused(ITEM, "groupBy", "note", "cannot be grouped");
    refused(ITEM, "groupBy", "qty", "cannot be grouped");
    refused(ITEM, "groupBy", "tags", "several values");
    refused(ITEM, "groupBy", "nope", "unknown field");
    refused(ITEM, "groupBy", "kind; drop table x", "unknown field");
    refused(ITEM, "groupBy", "status", "unknown field");
  }

  @Test
  void aDateNeedsMonthAndNothingElseTakesIt() {
    refused(ITEM, "groupBy", "due", "due:month");
    refused(ITEM, "groupBy", "created_at", "created_at:month");
    refused(ITEM, "groupBy", "kind:month", "date field only");
  }

  @Test
  void sumAverageAndBoundsTakeANumberField() {
    assertThat(parse(ITEM, "metrics", "count,sum(qty),avg(qty),min(qty),max(qty)").metrics())
        .hasSize(5);
    refused(ITEM, "metrics", "sum(note)", "not a number field");
    refused(ITEM, "metrics", "sum", "needs a number field");
    refused(ITEM, "metrics", "sum(nope)", "unknown field");
    refused(ITEM, "metrics", "count(qty)", "takes no field");
    refused(ITEM, "metrics", "median(qty)", "unknown figure");
    refused(ITEM, "metrics", "sum(qty);drop", "unknown figure");
  }

  @Test
  void aFigureIsAskedOnceAndInASmallNumber() {
    refused(ITEM, "metrics", "count,count", "asked twice");
    refused(
        ITEM,
        "metrics",
        "count,sum(qty),avg(qty),min(qty),max(qty),sum(qty ),min(qty ),max(qty )",
        "at most");
  }

  @Test
  void theNumberOfGroupsIsKeptWithinBounds() {
    assertThat(parse(ITEM, "limit", "0").limit()).isEqualTo(1);
    assertThat(parse(ITEM, "limit", "5000").limit()).isEqualTo(ReportQuery.MAX_LIMIT);
    refused(ITEM, "limit", "many", "whole number");
  }
}
