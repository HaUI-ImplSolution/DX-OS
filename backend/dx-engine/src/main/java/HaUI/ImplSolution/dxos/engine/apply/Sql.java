// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import java.util.regex.Pattern;

// Quoting of the identifiers that reach SQL text. Names come from a validated model; the check
// here refuses anything else.
public final class Sql {

  private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

  private Sql() {}

  public static String ident(String name) {
    if (!IDENTIFIER.matcher(name).matches()) {
      throw new IllegalStateException("Not a safe identifier: " + name);
    }
    return "\"" + name + "\"";
  }

  public static String table(Model model, Entity entity) {
    return ident(model.schema()) + "." + ident(entity.name());
  }

  public static String literal(String value) {
    return "'" + value.replace("'", "''") + "'";
  }
}
