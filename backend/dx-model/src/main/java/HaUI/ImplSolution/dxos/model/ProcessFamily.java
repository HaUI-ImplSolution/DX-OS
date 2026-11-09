// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// The states of a record, the actions between them and who may take them. A family is a ready made
// shape a model can start from; a model can also declare its own, and the process of an entity
// carries the one it ends up with. The steps of a family are a BPMN file named after it.
public record ProcessFamily(String name,String initial,List<State>states,List<Action>actions){

// Who may take an action: the role that handles the record, or the user who submitted it.
public enum Actor {
  HANDLER, OWNER

  }

  // A record in a terminal state can no longer change.
  public record State(String name, String label, boolean terminal) {}

  // The comparison of one field with a value that decides whether an action can be taken. The
  // operator is one of ==, !=, <, <=, > and >=, kept as written so that a client compares by the
  // same words. The value is a number for a number field and text otherwise. Equality works on any
  // field the parser allows, ordering only on numbers; a field without a value never meets a
  // condition.
  public record Condition(String field, String op, Object value) {

  public static final List<String> OPERATORS = List.of("==", "!=", "<=", ">=", "<", ">");

  public static boolean ordering(String op) {
    return !"==".equals(op) && !"!=".equals(op);
  }

  // Whether the value a record holds in the field meets the condition.
  public boolean test(Object actual) {
    if (actual == null) {
      return false;
    }
    if (value instanceof BigDecimal expected) {
      try {
        return compare(new BigDecimal(actual.toString()).compareTo(expected));
      } catch (NumberFormatException notNumber) {
        return false;
      }
    }
    return compare(actual.toString().equals(value) ? 0 : 1);
  }

  private boolean compare(int order) {
    return switch (op) {
      case "==" -> order == 0;
      case "!=" -> order != 0;
      case "<" -> order < 0;
      case "<=" -> order <= 0;
      case ">" -> order > 0;
      case ">=" -> order >= 0;
      default -> false;
    };
  }

  public String text() {
    return field + " " + op + " "
        + (value instanceof BigDecimal number ? number.toPlainString() : value);
  }}

  // An action moves a record out of one of the states in "from" into the state "to". A handler
  // action needs a note when the owner must learn why. Roles, when not empty, are the roles that
  // may take a handler action instead of the handler roles of the process. Require names the
  // fields that must hold a value before the action can be taken. When, if given, is a comparison
  // of one field of the record that must hold before the action can be taken.
  public record Action(String name, String label, List<String> from, String to, Actor by,
      boolean needsNote, List<String> roles, List<String> require, Condition when) {

  public Action(String name, String label, List<String> from, String to, Actor by,
        boolean needsNote) {
      this(name, label, from, to, by, needsNote, List.of(), List.of(), null);
    }

  public boolean allowedIn(String state) {
    return from.contains(state);
  }}

  public static final String WITHDRAW = "withdraw";
  public static final String WITHDRAWN_STATE = "withdrawn";

  public static final State WITHDRAWN = new State(WITHDRAWN_STATE, "Đã rút lại", true);
  public static final String WITHDRAW_LABEL = "Rút lại";

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
              new Action(WITHDRAW, WITHDRAW_LABEL, List.of("submitted", "returned"),
                  WITHDRAWN_STATE, Actor.OWNER, false))),
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
              new Action(WITHDRAW, WITHDRAW_LABEL, List.of("submitted", "in_progress", "resolved"),
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
              new Action(WITHDRAW, WITHDRAW_LABEL, List.of("requested", "confirmed"),
                  WITHDRAWN_STATE, Actor.OWNER, false))));

  // The action a work_item record that waits for its owner is closed with when auto_close is given
  // as a time only. A family not listed has no such default.
  private static final Map<String, String> AUTO_CLOSE_ACTION = Map.of("work_item", "confirm");

  public static Optional<String> defaultAutoCloseAction(String familyName) {
    return Optional.ofNullable(AUTO_CLOSE_ACTION.get(familyName));
  }

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

  // Whether two definitions run the same steps. Labels, who may take an action and the fields it
  // requires are checked outside the steps, and withdrawing ends the process from outside, so none
  // of them change the steps. The condition of an action decides where a record may go, so it does.
  public boolean sameFlowAs(ProcessFamily other) {
    return initial.equals(other.initial) && flowStates().equals(other.flowStates())
        && flowActions().equals(other.flowActions());
  }

  private List<String> flowStates() {
    return states.stream().filter(state -> !WITHDRAWN_STATE.equals(state.name()))
        .map(state -> state.name() + (state.terminal() ? "!" : "")).toList();
  }

  private List<String> flowActions() {
    return actions.stream().filter(action -> !WITHDRAW.equals(action.name()))
        .map(action -> action.name() + ":" + String.join(",", action.from()) + ">" + action.to()
            + ":" + action.by() + (action.when() == null ? "" : "?" + action.when().text()))
        .toList();
  }

  // Whether a handler can act on a record in this state, which is when its deadline runs.
  public boolean awaitsHandler(String stateName) {
    return actions.stream()
        .anyMatch(action -> action.by() == Actor.HANDLER && action.allowedIn(stateName));
  }
}
