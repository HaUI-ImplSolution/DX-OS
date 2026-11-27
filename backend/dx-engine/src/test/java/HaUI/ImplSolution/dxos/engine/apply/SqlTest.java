// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.ModelParser;
import org.junit.jupiter.api.Test;

// Names that come from a model file reach SQL text, so only plain names may pass.
class SqlTest {

  @Test
  void aPlainNameIsQuoted() {
    assertEquals("\"title\"", Sql.ident("title"));
    assertEquals("\"_a1\"", Sql.ident("_a1"));
  }

  @Test
  void aNameThatCouldChangeTheStatementIsRefused() {
    for (String name :
        new String[] {
          "title\"; DROP TABLE x; --", "a b", "Title", "1abc", "", "a-b", "a.b", "x".repeat(64)
        }) {
      assertThrows(IllegalStateException.class, () -> Sql.ident(name), name);
    }
  }

  @Test
  void aNameOfSixtyThreeCharactersIsTheLongestPostgresqlKeeps() {
    String longest = "x".repeat(63);

    assertEquals("\"" + longest + "\"", Sql.ident(longest));
  }

  @Test
  void aConstraintNameMayCarryADollarAndNothingElseOdd() {
    assertEquals("\"uq$item$title\"", Sql.constraint("uq$item$title"));
    assertThrows(IllegalStateException.class, () -> Sql.constraint("uq item"));
    assertThrows(IllegalStateException.class, () -> Sql.constraint("uq\";"));
  }

  @Test
  void aLiteralDoublesItsQuotes() {
    assertEquals("'it''s'", Sql.literal("it's"));
    assertEquals("''''", Sql.literal("'"));
    assertEquals("''", Sql.literal(""));
  }

  @Test
  void aTableIsTheSchemaOfTheModelAndTheNameOfTheEntity() {
    Model model =
        ModelParser.parse(
            "format: 1\nname: shop\nroles:\n  - name: A\nentities:\n  item:\n    fields:\n"
                + "      a: {type: text}\n    access: {A: all}\n");

    assertEquals(
        "\"" + model.schema() + "\".\"item\"", Sql.table(model, model.entities().get("item")));
  }
}
