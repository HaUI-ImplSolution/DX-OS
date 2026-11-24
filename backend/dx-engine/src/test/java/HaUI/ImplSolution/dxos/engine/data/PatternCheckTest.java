// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PatternCheckTest {

  @Test
  void theWholeTextMustMatch() {
    assertTrue(PatternCheck.matches("[A-Z]{3}-[0-9]{4}", "ABC-1234"));
    assertFalse(PatternCheck.matches("[A-Z]{3}-[0-9]{4}", "ABC-12345"));
    assertFalse(PatternCheck.matches("[A-Z]{3}-[0-9]{4}", "xABC-1234"));
  }

  // The JDK remembers the states of most nested loops, so a back reference is what still makes a
  // match run away; the budget has to stop it.
  @Test
  void aPatternThatBacktracksWithoutEndStopsOverBudget() {
    String evil = "(?:(a)|aa)+\\1$";
    String text = "a".repeat(40) + "!";

    assertTimeoutPreemptively(
        Duration.ofSeconds(20),
        () -> assertThrows(PatternCheck.TooComplex.class, () -> PatternCheck.matches(evil, text)));
  }

  @Test
  void aPatternThatIsSlowOnOneTextStillWorksOnAnother() {
    assertTrue(PatternCheck.matches("(a+)+", "aaaa"));
  }
}
