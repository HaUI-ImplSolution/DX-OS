// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

// Checks a text against the pattern of its field. A pattern is compiled once, and a match may read
// only so many characters, so a pattern that backtracks for long fails the value instead of holding
// the request.
final class PatternCheck {

  // The longest text a pattern is run against.
  static final int MAX_LENGTH = 1000;
  private static final int READ_BUDGET = 2_000_000;
  private static final Map<String, Pattern> COMPILED = new ConcurrentHashMap<>();

  // The match took more reads than the budget allows.
  static final class TooComplex extends RuntimeException {
    TooComplex() {
      super("pattern match over budget", null, false, false);
    }
  }

  private PatternCheck() {}

  // Whether the whole text matches; throws TooComplex when the match runs over its budget.
  static boolean matches(String pattern, String text) {
    return COMPILED.computeIfAbsent(pattern, Pattern::compile).matcher(new Counted(text)).matches();
  }

  private static final class Counted implements CharSequence {
    private final String text;
    private int reads;

    Counted(String text) {
      this.text = text;
    }

    @Override
    public int length() {
      return text.length();
    }

    @Override
    public char charAt(int index) {
      if (++reads > READ_BUDGET) {
        throw new TooComplex();
      }
      return text.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return text.subSequence(start, end);
    }

    @Override
    public String toString() {
      return text;
    }
  }
}
