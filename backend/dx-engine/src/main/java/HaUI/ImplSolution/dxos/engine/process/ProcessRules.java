// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.core.api.exception.ActionNotAllowedException;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Action;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Actor;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

// The rules every process obeys, whichever family it runs. A model cannot switch them off.
@Component
public class ProcessRules {

  // Whether the caller may take the action on the record, judged from the record as it is locked.
  public void checkAction(Entity entity, Action action, Map<String, Object> record, String me,
      Set<String> roles, String note) {
    boolean creator = me.equals(record.get("created_by"));
    if (action.by() == Actor.OWNER && !creator) {
      throw new ActionNotAllowedException("Only the owner of the record can " + action.name());
    }
    if (action.by() == Actor.HANDLER && !roles.contains(entity.process().handler())) {
      throw new ActionNotAllowedException("Only " + entity.process().handler() + " can do this");
    }
    String status = (String) record.get("status");
    if (!action.allowedIn(status)) {
      throw new InvalidStateException("The record cannot take this action in state " + status);
    }
    if (action.by() == Actor.HANDLER) {
      if (!entity.process().allowSelfDecision() && creator) {
        throw new ActionNotAllowedException("The creator of a record cannot handle it");
      }
      Object assignee = record.get("assignee");
      if (assignee != null && !me.equals(assignee)) {
        throw new ActionNotAllowedException("The record is assigned to someone else");
      }
    }
    if (action.needsNote() && (note == null || note.isBlank())) {
      throw new ModelValidationException("note", "is required");
    }
  }
}
