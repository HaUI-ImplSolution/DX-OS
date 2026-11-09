// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import java.util.Set;

// The keys a process block of a model file accepts, at each level. The parser reads them from
// here, so anything that tells a user what can be written reads the same lists.
public final class ProcessSyntax {

  public static final Set<String> PROCESS = Set.of("family", "handler", "initial", "states",
      "actions", "allow_self_decision", "deadline", "auto_close", "after", "booking");
  public static final Set<String> STATE = Set.of("label", "terminal");
  public static final Set<String> ACTION =
      Set.of("label", "from", "to", "by", "role", "needs_note", "require");
  public static final Set<String> AUTO_CLOSE = Set.of("after", "action");

  private ProcessSyntax() {}
}
