// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.ProcessFamily;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Action;
import java.util.Map;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Starts, advances, withdraws and cancels the process of a record. Callers run inside a
// transaction; the workflow engine joins it.
@Service
public class ProcessService {

  private final RuntimeService runtime;
  private final TaskService tasks;
  private final RecordStatus recordStatus;
  private final boolean faultAfterDecision;

  public ProcessService(
      RuntimeService runtime,
      TaskService tasks,
      RecordStatus recordStatus,
      @Value("${engine.fault.after-decision:false}") boolean faultAfterDecision) {
    this.runtime = runtime;
    this.tasks = tasks;
    this.recordStatus = recordStatus;
    this.faultAfterDecision = faultAfterDecision;
  }

  public void start(Model model, Entity entity, long id, String ownerId) {
    String processKey = entity.process().processKey(model.name(), entity.name());
    try {
      runtime.startProcessInstanceByKey(
          processKey,
          key(model, entity, id),
          Map.of(
              "handlerRole",
              String.join(",", entity.process().handlers()),
              "ownerId",
              ownerId,
              "model",
              model.name(),
              "entity",
              entity.name(),
              "recordId",
              id));
    } catch (FlowableObjectNotFoundException e) {
      throw new IllegalStateException("The process " + processKey + " is not deployed", e);
    }
  }

  // Completes the open task of the record with an action; the process then writes the new status.
  public void act(Model model, Entity entity, long id, Action action, String actor) {
    Task task =
        tasks
            .createTaskQuery()
            .processInstanceBusinessKey(key(model, entity, id))
            .active()
            .singleResult();
    if (task == null) {
      throw new InvalidStateException("The record cannot take this action now", "WRONG_STATE");
    }
    try {
      tasks.setAssignee(task.getId(), actor);
      tasks.complete(task.getId(), Map.of("action", action.name(), "actor", actor));
    } catch (FlowableException e) {
      throw new InvalidStateException("The record cannot take this action now", "WRONG_STATE");
    }
    if (faultAfterDecision) {
      throw new IllegalStateException("Fault injected after the action");
    }
  }

  // Ends the process of a record the owner no longer wants; the record stays, in a final state.
  public void withdraw(Model model, Entity entity, long id, String actor) {
    cancel(model, entity, id, "withdrawn");
    recordStatus.write(model, entity, id, ProcessFamily.WITHDRAWN_STATE, actor);
  }

  // Marks who works on the open task; null leaves it open to the handler role.
  public void assign(Model model, Entity entity, long id, String status, String assignee) {
    if (!entity.process().definition().awaitsHandler(status)) {
      return;
    }
    Task task =
        tasks
            .createTaskQuery()
            .processInstanceBusinessKey(key(model, entity, id))
            .active()
            .singleResult();
    if (task != null) {
      tasks.setAssignee(task.getId(), assignee);
    }
  }

  public void cancel(Model model, Entity entity, long id, String reason) {
    ProcessInstance instance =
        runtime
            .createProcessInstanceQuery()
            .processInstanceBusinessKey(key(model, entity, id))
            .singleResult();
    if (instance != null) {
      runtime.deleteProcessInstance(instance.getId(), reason);
    }
  }

  private static String key(Model model, Entity entity, long id) {
    return model.name() + "." + entity.name() + "." + id;
  }
}
