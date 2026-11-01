// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.ProcessTemplate.Action;
import HaUI.ImplSolution.dxos.model.ProcessTemplate.Actor;
import java.util.Map;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Starts, decides and cancels the process of a record. Callers run inside a transaction; the
// workflow engine joins it.
@Service
public class ProcessService {

  private final RuntimeService runtime;
  private final TaskService tasks;
  private final boolean faultAfterDecision;

  public ProcessService(RuntimeService runtime, TaskService tasks,
      @Value("${engine.fault.after-decision:false}") boolean faultAfterDecision) {
    this.runtime = runtime;
    this.tasks = tasks;
    this.faultAfterDecision = faultAfterDecision;
  }

  public void start(Model model, Entity entity, long id, String ownerId) {
    runtime.startProcessInstanceByKey(entity.process().template(), key(model, entity, id),
        Map.of("approverRole", entity.process().approver(), "ownerId", ownerId, "model",
            model.name(), "entity", entity.name(), "recordId", id));
  }

  // Completes the task behind an action; the process then writes the new status of the record.
  public void act(Model model, Entity entity, long id, Action action, String actor) {
    Task task = tasks.createTaskQuery().processInstanceBusinessKey(key(model, entity, id))
        .taskDefinitionKey(action.task()).active().singleResult();
    if (task == null) {
      throw new InvalidStateException("The record cannot take this action now");
    }
    try {
      tasks.setAssignee(task.getId(), actor);
      tasks.complete(task.getId(), Map.of("action", action.name(), "actor", actor));
    } catch (FlowableException e) {
      throw new InvalidStateException("The record cannot take this action now");
    }
    if (faultAfterDecision) {
      throw new IllegalStateException("Fault injected after the action");
    }
  }

  // Marks who works on the open task; null leaves it open to the handler role.
  public void assign(Model model, Entity entity, long id, String assignee) {
    Task task = tasks.createTaskQuery().processInstanceBusinessKey(key(model, entity, id)).active()
        .singleResult();
    boolean handlerTask = task != null && entity.process().definition().actions().stream()
        .anyMatch(action -> action.by() == Actor.HANDLER
            && action.task().equals(task.getTaskDefinitionKey()));
    if (handlerTask) {
      tasks.setAssignee(task.getId(), assignee);
    }
  }

  public void cancel(Model model, Entity entity, long id) {
    ProcessInstance instance = runtime.createProcessInstanceQuery()
        .processInstanceBusinessKey(key(model, entity, id)).singleResult();
    if (instance != null) {
      runtime.deleteProcessInstance(instance.getId(), "record deleted");
    }
  }

  private static String key(Model model, Entity entity, long id) {
    return model.name() + "." + entity.name() + "." + id;
  }
}
