// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.model.Booking;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Action;
import HaUI.ImplSolution.dxos.model.ProcessFamily.State;
import java.util.List;

// The workflow an entity runs, as shown to clients; the deadline is in seconds, or null.
public record ProcessInfo(
    String family,
    List<String> handlers,
    boolean allowSelfDecision,
    Long deadlineSeconds,
    Booking booking,
    List<State> states,
    List<Action> actions) {}
