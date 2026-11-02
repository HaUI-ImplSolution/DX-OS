// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// A process a model can choose: the states of a record, the actions between them and who may take
// them. The steps themselves are a BPMN file of the same name in the service.
public record ProcessTemplate(String name,String initial,List<State>states,List<Action>actions){

// Who may take an action: the role that handles the record, or the user who submitted it.
public enum Actor {
  HANDLER, OWNER

  }

  // A record in a terminal state can no longer change.
  public record State(String name, String label, boolean terminal) {}

  // An action moves a record out of the state "from" by completing the task "task" of the process.
  public record Action(String name, String label, String from, Actor by, boolean needsNote,
      String task) {}

  private static final Map<String, ProcessTemplate> ALL = Map.of("approval",
      new ProcessTemplate("approval", "submitted",
          List.of(new State("submitted", "Chờ xử lý", false),
              new State("returned", "Cần bổ sung", false), new State("approved", "Đã duyệt", true),
              new State("rejected", "Từ chối", true)),
          List.of(new Action("approve", "Duyệt", "submitted", Actor.HANDLER, false, "decide"),
              new Action("reject", "Từ chối", "submitted", Actor.HANDLER, false, "decide"),
              new Action("return", "Yêu cầu bổ sung", "submitted", Actor.HANDLER, true, "decide"),
              new Action("resubmit", "Nộp lại", "returned", Actor.OWNER, false, "resubmit"))));

  public static Optional<ProcessTemplate> find(String name) {
    return Optional.ofNullable(ALL.get(name));
  }

  public static Set<String> names() {
    return ALL.keySet();
  }

  public Optional<Action> action(String actionName) {
    return actions.stream().filter(action -> action.name().equals(actionName)).findFirst();
  }

  public boolean isTerminal(String stateName) {
    return states.stream().anyMatch(state -> state.name().equals(stateName) && state.terminal());
  }
}
