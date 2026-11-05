// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// A shape of process a model can choose: the states of a record, the actions between them and
// who may take them. The steps themselves are a BPMN file named after the family.
public record ProcessFamily(String name,String initial,List<State>states,List<Action>actions){

// Who may take an action: the role that handles the record, or the user who submitted it.
public enum Actor {
  HANDLER, OWNER

  }

  // A record in a terminal state can no longer change.
  public record State(String name, String label, boolean terminal) {}

  // An action moves a record out of one of the states in "from" into the state "to". A handler
  // action needs a note when the owner must learn why.
  public record Action(String name, String label, List<String> from, String to, Actor by,
      boolean needsNote) {

  public boolean allowedIn(String state) {
    return from.contains(state);
  }}

  // A work_item record that stays resolved is closed with the action its owner would take.
  public static final String AUTO_CLOSE_STATE = "resolved";
  public static final String AUTO_CLOSE_ACTION = "confirm";
  public static final String AUTO_CLOSE_FROM_ACTION = "resolve";

  public static final String WITHDRAW = "withdraw";
  public static final String WITHDRAWN_STATE = "withdrawn";

  private static final State WITHDRAWN = new State(WITHDRAWN_STATE, "Đã rút lại", true);

  private static final Map<String, ProcessFamily> ALL = Map.of("approval",
      new ProcessFamily("approval", "submitted",
          List.of(new State("submitted", "Chờ xử lý", false),
              new State("returned", "Cần bổ sung", false), new State("approved", "Đã duyệt", true),
              new State("rejected", "Từ chối", true), WITHDRAWN),
          List.of(
              new Action("approve", "Duyệt", List.of("submitted"), "approved", Actor.HANDLER,
                  false),
              new Action("reject", "Từ chối", List.of("submitted"), "rejected", Actor.HANDLER,
                  true),
              new Action("return", "Yêu cầu bổ sung", List.of("submitted"), "returned",
                  Actor.HANDLER, true),
              new Action("resubmit", "Nộp lại", List.of("returned"), "submitted", Actor.OWNER,
                  false),
              new Action(WITHDRAW, "Rút lại", List.of("submitted", "returned"), WITHDRAWN_STATE,
                  Actor.OWNER, false))),
      "work_item",
      new ProcessFamily("work_item", "submitted",
          List.of(new State("submitted", "Mới", false),
              new State("in_progress", "Đang xử lý", false),
              new State("resolved", "Đã xử lý xong", false), new State("closed", "Đã đóng", true),
              new State("rejected", "Từ chối", true), WITHDRAWN),
          List.of(
              new Action("start", "Nhận xử lý", List.of("submitted"), "in_progress", Actor.HANDLER,
                  false),
              new Action("resolve", "Xử lý xong", List.of("in_progress"), "resolved", Actor.HANDLER,
                  false),
              new Action("reject", "Từ chối", List.of("submitted", "in_progress"), "rejected",
                  Actor.HANDLER, true),
              new Action("confirm", "Xác nhận xong", List.of("resolved"), "closed", Actor.OWNER,
                  false),
              new Action("reopen", "Mở lại", List.of("resolved"), "in_progress", Actor.OWNER, true),
              new Action(WITHDRAW, "Rút lại", List.of("submitted", "in_progress", "resolved"),
                  WITHDRAWN_STATE, Actor.OWNER, false))),
      "reservation",
      new ProcessFamily("reservation", "requested",
          List.of(new State("requested", "Chờ xác nhận", false),
              new State("confirmed", "Đã xác nhận", false),
              new State("in_use", "Đang sử dụng", false),
              new State("completed", "Đã hoàn tất", true), new State("rejected", "Từ chối", true),
              new State("no_show", "Vắng mặt", true), WITHDRAWN),
          List.of(
              new Action("confirm", "Xác nhận", List.of("requested"), "confirmed", Actor.HANDLER,
                  false),
              new Action("reject", "Từ chối", List.of("requested"), "rejected", Actor.HANDLER,
                  true),
              new Action("start", "Bắt đầu sử dụng", List.of("confirmed"), "in_use", Actor.HANDLER,
                  false),
              new Action("no_show", "Vắng mặt", List.of("confirmed"), "no_show", Actor.HANDLER,
                  false),
              new Action("complete", "Hoàn tất", List.of("in_use"), "completed", Actor.HANDLER,
                  false),
              new Action(WITHDRAW, "Rút lại", List.of("requested", "confirmed"), WITHDRAWN_STATE,
                  Actor.OWNER, false))));

  public static Optional<ProcessFamily> find(String name) {
    return Optional.ofNullable(ALL.get(name));
  }

  public static Set<String> names() {
    return new java.util.TreeSet<>(ALL.keySet());
  }

  public Optional<Action> action(String actionName) {
    return actions.stream().filter(action -> action.name().equals(actionName)).findFirst();
  }

  public boolean isTerminal(String stateName) {
    return states.stream().anyMatch(state -> state.name().equals(stateName) && state.terminal());
  }

  // Whether a record that waits for its owner can be closed by the system after a time.
  public boolean supportsAutoClose() {
    return "work_item".equals(name);
  }

  // Whether a handler can act on a record in this state, which is when its deadline runs.
  public boolean awaitsHandler(String stateName) {
    return actions.stream()
        .anyMatch(action -> action.by() == Actor.HANDLER && action.allowedIn(stateName));
  }
}
