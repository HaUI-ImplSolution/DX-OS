// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextSearchTest {

  @Test
  void foldingRemovesVietnameseMarksAndCase() {
    assertEquals("sot xuat huyet", TextSearch.fold("Sốt Xuất Huyết"));
    assertEquals("nguyen van a", TextSearch.fold("NGUYỄN VĂN A"));
    assertEquals("duong dinh", TextSearch.fold("Đường Đình"));
  }

  @Test
  void aWordWithoutMarksFoldsToItself() {
    assertEquals("hello world 42", TextSearch.fold("hello world 42"));
  }

  @Test
  void aSearchWithoutMarksMeetsTheTextWithMarks() {
    assertTrue(TextSearch.fold("Hồ sơ hoãn thi").contains(TextSearch.fold("hoan thi")));
  }

  @Test
  void theSqlTableFoldsTheSameLettersAsTheJavaFunction() {
    assertEquals(TextSearch.FROM.length(), TextSearch.TO.length());
    for (int i = 0; i < TextSearch.FROM.length(); i++) {
      assertEquals(
          TextSearch.fold(String.valueOf(TextSearch.FROM.charAt(i))),
          String.valueOf(TextSearch.TO.charAt(i)),
          "letter " + TextSearch.FROM.charAt(i));
    }
    assertTrue(TextSearch.FROM.contains("ế"));
    assertTrue(TextSearch.FROM.contains("Đ".toLowerCase().equals("đ") ? "đ" : "d"));
  }

  @Test
  void theWildcardsOfAWordAreTakenLiterally() {
    assertEquals("%100\\%%", TextSearch.contains("100%"));
    assertEquals("%a\\_b%", TextSearch.contains("a_b"));
    assertEquals("%a\\\\b%", TextSearch.contains("a\\b"));
    assertEquals("%plain%", TextSearch.contains("plain"));
  }
}
