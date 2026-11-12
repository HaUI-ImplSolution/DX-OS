// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.model.Booking;
import HaUI.ImplSolution.dxos.model.Escalation;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Action;
import HaUI.ImplSolution.dxos.model.ProcessFamily.State;
import java.util.List;

// The workflow an entity runs, as shown to clients; the deadline is in seconds, or null.
public record ProcessInfo(
    String family,
    List<String> handlers,
    boolean allowSelfDecision,
    Long deadlineSeconds,
    List<EscalationInfo> escalations,
    Booking booking,
    List<State> states,
    List<Action> actions) {

  // A level of escalation in seconds past the deadline; everySeconds is null for a single notice.
  public record EscalationInfo(long afterSeconds, List<String> to, Long everySeconds, int times) {

    public static List<EscalationInfo> of(List<Escalation> levels) {
      return levels.stream()
          .map(
              level ->
                  new EscalationInfo(
                      level.after().toSeconds(),
                      level.to(),
                      level.every() == null ? null : level.every().toSeconds(),
                      level.times()))
          .toList();
    }
  }
}
