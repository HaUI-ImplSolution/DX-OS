// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
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

  public void start(Model model, Entity entity, long id) {
    runtime.startProcessInstanceByKey(entity.process().template(), key(model, entity, id),
        Map.of("approverRole", entity.process().approver(), "model", model.name(), "entity",
            entity.name(), "recordId", id));
  }

  public void decide(Model model, Entity entity, long id, String outcome, String decidedBy) {
    Task task = tasks.createTaskQuery().processInstanceBusinessKey(key(model, entity, id)).active()
        .singleResult();
    if (task == null) {
      throw new InvalidStateException("The record is already decided");
    }
    try {
      tasks.setAssignee(task.getId(), decidedBy);
      tasks.complete(task.getId(), Map.of("decision", outcome, "decidedBy", decidedBy));
    } catch (FlowableException e) {
      throw new InvalidStateException("The record is already decided");
    }
    if (faultAfterDecision) {
      throw new IllegalStateException("Fault injected after the decision");
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
