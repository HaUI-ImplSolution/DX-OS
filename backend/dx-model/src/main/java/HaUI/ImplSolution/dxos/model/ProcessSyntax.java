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
      Set.of("label", "from", "to", "by", "role", "needs_note", "require", "when");
  public static final Set<String> AUTO_CLOSE = Set.of("after", "action");

  // Names the history of a record uses for the lines the platform writes, so an action cannot
  // take them: its own line would be taken for the one of the platform.
  public static final Set<String> HISTORY_NAMES =
      Set.of("created", "updated", "deleted", "assigned", "file_added", "file_removed",
          "completion_skipped", "auto_closed", "overdue", "due_soon");

  private ProcessSyntax() {}
}
