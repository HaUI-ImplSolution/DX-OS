// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import HaUI.ImplSolution.dxos.model.ProcessFamily.Comparison;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Condition;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Group;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConditionTextTest {

  // Numbers become numbers, as the model reader does for a numeric field; any other word stays
  // text.
  private static final ConditionText.Leaf LEAF =
      (field, op, value) ->
          new Comparison(field, op, value.matches("[0-9.]+") ? new BigDecimal(value) : value);

  private static Condition read(String text) {
    return new ConditionText(text).parse(LEAF);
  }

  private static String problem(String text) {
    return assertThrows(ConditionText.Syntax.class, () -> read(text)).getMessage();
  }

  private static Comparison compare(String field, String op, Object value) {
    return new Comparison(field, op, value);
  }

  @Test
  void readsOneComparison() {
    assertEquals(compare("amount", ">", new BigDecimal("5000000")), read("amount > 5000000"));
  }

  @Test
  void operatorsNeedNoSpaces() {
    assertEquals(compare("amount", ">=", new BigDecimal("5")), read("amount>=5"));
    assertEquals(compare("kind", "!=", "late"), read("kind!=late"));
  }

  @Test
  void andBindsTighterThanOr() {
    Condition read = read("a == x or b == y and c == z");

    assertEquals(
        new Group(
            "or",
            List.of(
                compare("a", "==", "x"),
                new Group("and", List.of(compare("b", "==", "y"), compare("c", "==", "z"))))),
        read);
  }

  @Test
  void notNegatesOnlyTheNextComparison() {
    Condition read = read("not a == x and b == y");

    assertEquals(
        new Group(
            "and",
            List.of(new Group("not", List.of(compare("a", "==", "x"))), compare("b", "==", "y"))),
        read);
  }

  @Test
  void parenthesesChangeTheOrderOfEvaluation() {
    Condition read = read("(a == x or b == y) and c == z");

    assertEquals(
        new Group(
            "and",
            List.of(
                new Group("or", List.of(compare("a", "==", "x"), compare("b", "==", "y"))),
                compare("c", "==", "z"))),
        read);
  }

  @Test
  void aQuotedValueKeepsItsSpacesAndWordsLikeAnd() {
    assertEquals(compare("reason", "==", "Hoãn thi"), read("reason == \"Hoãn thi\""));
    assertEquals(compare("word", "==", "and"), read("word == 'and'"));
  }

  @Test
  void aFieldNamedNotIsStillAField() {
    assertEquals(compare("not", "==", "x"), read("not == x"));
  }

  @Test
  void theConditionDecidesForARecord() {
    Condition read = read("amount > 5000000 and not status == done");

    assertTrue(read.test(Map.of("amount", 8_000_000, "status", "open")));
    assertFalse(read.test(Map.of("amount", 8_000_000, "status", "done")));
    assertFalse(read.test(Map.of("amount", 3_000_000, "status", "open")));
  }

  @Test
  void anEmptyFieldMeetsNoComparisonNotEvenNotEqual() {
    Condition different = read("kind != late");

    assertFalse(different.test(Map.of()));
    assertTrue(read("not kind != late").test(Map.of()));
  }

  @Test
  void aTextThatIsNotANumberDoesNotMeetANumericComparison() {
    assertFalse(read("amount > 5").test(Map.of("amount", "many")));
  }

  @Test
  void datesAreComparedAsDates() {
    Condition read = read("due < 2026-12-25");

    assertTrue(read.test(Map.of("due", "2026-12-24")));
    assertFalse(read.test(Map.of("due", "2026-12-25")));
    assertFalse(read.test(Map.of("due", "tomorrow")));
  }

  @Test
  void reportsWhatIsWrongWithTheText() {
    assertEquals("a parenthesis is not closed", problem("(a == x"));
    assertEquals("a quote is not closed", problem("a == \"x"));
    assertEquals("unexpected 'b'", problem("a == x b"));
    assertEquals("a value is missing after a ==", problem("a =="));
    assertEquals("expected an operator after a, found the end", problem("a"));
    assertEquals("'Amount' is not a field name", problem("Amount > 5"));
  }

  @Test
  void parenthesesNestThreeDeepAtMost() {
    assertEquals(compare("a", "==", "x"), read("(((a == x)))"));
    assertEquals("parentheses nest deeper than 3", problem("((((a == x))))"));
  }

  @Test
  void aConditionHasEightComparisonsAtMost() {
    String eight = String.join(" and ", java.util.Collections.nCopies(8, "a == x"));
    String nine = String.join(" and ", java.util.Collections.nCopies(9, "a == x"));

    assertEquals(8, ((Group) read(eight)).conditions().size());
    assertEquals("there are more than 8 comparisons", problem(nine));
  }
}
