// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.model.Booking;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Action;
import HaUI.ImplSolution.dxos.model.ProcessFamily.State;
import java.util.List;
import java.util.Map;

// The workflow an entity runs, as shown to clients; the deadline is in seconds, or null.
// The shared formatter uses pre-record syntax; preserve the vertical record layout.
// @formatter:off
public record ProcessInfo(
  String family,
  List<String> handlers,
  boolean allowSelfDecision,
  Long deadlineSeconds,
  Booking booking,
  List<State> states,
  List<Action> actions,
  Map<String, List<Effect>> effects
) {

  // These effects are queued after the decision, not completed by the action response.
  public record Effect(
    String operation,
    String user,
    String group,
    String groupField,
    boolean asynchronous
  ) {}
}
// @formatter:on
