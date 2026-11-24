// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CodeFormatTest {

  private static String problem(String format) {
    return assertThrows(IllegalArgumentException.class, () -> CodeFormat.parse(format))
        .getMessage();
  }

  @Test
  void writesFixedTextTheYearAndTheSequenceWithItsWidth() {
    CodeFormat format = CodeFormat.parse("HS-{year}-{seq:5}");

    assertEquals("HS-2026-00007", format.render(2026, 7));
    assertEquals("HS-2026-12345", format.render(2026, 12345));
    assertEquals(5, format.width());
    assertEquals(99_999, format.last());
  }

  @Test
  void aFormatWithAYearCountsEachYearApartAndOneWithoutCountsAllTogether() {
    CodeFormat yearly = CodeFormat.parse("TU-{year}-{seq:4}");
    CodeFormat plain = CodeFormat.parse("TU{seq:4}");

    assertTrue(yearly.yearly());
    assertEquals("2026", yearly.period(2026));
    assertEquals("2027", yearly.period(2027));
    assertFalse(plain.yearly());
    assertEquals("-", plain.period(2026));
    assertEquals("-", plain.period(2027));
    assertEquals("TU0042", plain.render(2026, 42));
  }

  @Test
  void aSequenceLongerThanItsWidthIsNotCut() {
    CodeFormat format = CodeFormat.parse("A-{seq:2}");

    assertEquals("A-1000", format.render(2026, 1000));
    assertEquals(99, format.last());
  }

  @Test
  void aFormatNeedsAnySequence() {
    assertTrue(problem("HS-{year}").contains("needs {seq:N}"));
    assertTrue(problem("").contains("needs a format"));
    assertTrue(problem("   ").contains("needs a format"));
    assertTrue(problem(null).contains("needs a format"));
  }

  @Test
  void theYearAndTheSequenceAppearOnce() {
    assertTrue(problem("{year}-{year}-{seq:3}").contains("{year} twice"));
    assertTrue(problem("{seq:3}-{seq:3}").contains("{seq:N} twice"));
  }

  @Test
  void theSequenceHasOneToNineDigits() {
    assertTrue(problem("A{seq:0}").contains("1 to 9 digits"));
    assertTrue(problem("A{seq:10}").contains("1 to 9 digits"));
    assertEquals(999_999_999L, CodeFormat.parse("A{seq:9}").last());
  }

  @Test
  void fixedTextHoldsLettersDigitsAndFewMarksOnly() {
    assertTrue(problem("HS {seq:3}").contains("letters, digits"));
    assertTrue(problem("HS#{seq:3}").contains("letters, digits"));
    assertEquals("HS.A_B/C-007", CodeFormat.parse("HS.A_B/C-{seq:3}").render(2026, 7));
  }

  @Test
  void aCodeIsAtMostSixtyCharacters() {
    String longest = "A".repeat(60 - 3) + "{seq:3}";
    String tooLong = "A".repeat(60 - 2) + "{seq:3}";

    assertEquals(60, CodeFormat.parse(longest).render(2026, 1).length());
    assertTrue(problem(tooLong).contains("at most 60 characters"));
  }

  @Test
  void theYearCountsAsFourCharactersInTheLength() {
    String tooLong = "A".repeat(60 - 4 - 3 + 1) + "{year}{seq:3}";

    assertTrue(problem(tooLong).contains("at most 60 characters"));
  }
}
