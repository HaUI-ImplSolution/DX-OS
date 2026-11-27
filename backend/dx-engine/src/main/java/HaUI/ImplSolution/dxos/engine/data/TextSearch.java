// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import java.text.Normalizer;
import java.util.Locale;

// Matching that ignores case and diacritics, for Vietnamese and other Latin text: the words of a
// search and the stored value are both folded to lower-case ASCII.
final class TextSearch {
  static final int MAX_LENGTH = 200;
  static final int MAX_WORDS = 8;

  // The letters with a diacritic mark, in both cases, and the ASCII letter each one folds to, as
  // the arguments of the SQL translate function.
  static final String FROM;
  static final String TO;

  static {
    StringBuilder from = new StringBuilder();
    StringBuilder to = new StringBuilder();
    for (int c = 0xC0; c <= 0x1EF9; c++) {
      if (c > 0x24F && c < 0x1EA0) {
        continue;
      }
      String letter = String.valueOf((char) c);
      String folded = fold(letter);
      if (folded.length() == 1 && folded.charAt(0) < 0x80 && Character.isLetter(folded.charAt(0))) {
        from.append(letter);
        to.append(folded);
      }
    }
    FROM = from.toString();
    TO = to.toString();
  }

  private TextSearch() {}

  static String fold(String text) {
    String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    return plain.replace('đ', 'd').replace('Đ', 'd').toLowerCase(Locale.ROOT);
  }

  // A LIKE pattern that matches the word anywhere, with the wildcard characters of the word
  // taken literally.
  static String contains(String word) {
    return "%" + word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }
}
