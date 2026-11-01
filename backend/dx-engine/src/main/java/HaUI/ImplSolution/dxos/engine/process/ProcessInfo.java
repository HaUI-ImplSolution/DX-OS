// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.model.ProcessTemplate.Action;
import HaUI.ImplSolution.dxos.model.ProcessTemplate.State;
import java.util.List;

// The workflow an entity runs, as shown to clients.
public record ProcessInfo(String template,String approver,boolean allowSelfDecision,List<State>states,List<Action>actions){}
