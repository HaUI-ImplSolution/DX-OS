// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.core.api.exception.ActionNotAllowedException;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Action;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Actor;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

// The rules every process obeys, whichever states and actions it declares. A model cannot switch
// them off.
@Component
public class ProcessRules {

  // Whether the caller may take the action on the record, judged from the record as it is locked.
  public void checkAction(
      Entity entity,
      Action action,
      Map<String, Object> record,
      String me,
      Set<String> roles,
      String note) {
    boolean creator = me.equals(record.get("created_by"));
    if (action.by() == Actor.OWNER && !creator) {
      throw new ActionNotAllowedException(
          "Only the owner of the record can " + action.name(), "OWNER_ONLY");
    }
    List<String> allowed = entity.process().rolesFor(action);
    if (action.by() == Actor.HANDLER && Collections.disjoint(roles, allowed)) {
      throw new ActionNotAllowedException(
          "Only " + String.join(" or ", allowed) + " can do this", "HANDLER_ONLY");
    }
    String status = (String) record.get("status");
    if (!action.allowedIn(status)) {
      throw new InvalidStateException(
          "The record cannot take this action in state " + status, "WRONG_STATE");
    }
    if (action.by() == Actor.HANDLER) {
      if (!entity.process().allowSelfDecision() && creator) {
        throw new ActionNotAllowedException(
            "The creator of a record cannot handle it", "SELF_DECISION");
      }
      Object assignee = record.get("assignee");
      if (assignee != null && !me.equals(assignee)) {
        throw new ActionNotAllowedException(
            "The record is assigned to someone else", "ASSIGNED_TO_OTHER");
      }
      if (action.when() != null && !action.when().test(record.get(action.when().field()))) {
        throw new InvalidStateException(
            "The record does not meet the condition of this action: " + action.when().text(),
            "CONDITION_NOT_MET");
      }
    }
    if (action.needsNote() && (note == null || note.isBlank())) {
      throw new ModelValidationException("note", "is required");
    }
    for (String field : action.require()) {
      Object value = record.get(field);
      if (value == null || value.toString().isBlank()) {
        throw new ModelValidationException(field, "is required before " + action.name());
      }
    }
  }
}
