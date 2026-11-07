// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Puts the BPMN of the entities whose steps differ from their family into the workflow engine. An
// entity that keeps the steps of its family runs the BPMN of the family, which is always there.
// Callers run inside a transaction; the workflow engine joins it.
@Component
public class ProcessDeployer {

  private static final Logger log = LoggerFactory.getLogger(ProcessDeployer.class);

  private final RepositoryService repository;
  private final RuntimeService runtime;

  public ProcessDeployer(RepositoryService repository, RuntimeService runtime) {
    this.repository = repository;
    this.runtime = runtime;
  }

  // The key of the BPMN process the records of the entity run.
  public String keyOf(Model model, Entity entity) {
    return entity.process().processKey(model.name(), entity.name());
  }

  // Deploys the BPMN of every entity of the model that needs its own. Calling it again with the
  // same model changes nothing.
  public void deploy(Model model) {
    for (Entity entity : model.entities().values()) {
      if (entity.process() != null && entity.process().ownFlow()) {
        String key = keyOf(model, entity);
        String xml =
            ProcessBpmnGenerator.generate(key, entity.label(), entity.process().definition());
        repository.createDeployment().name(key).addString(key + ".bpmn20.xml", xml)
            .enableDuplicateFiltering().deploy();
      }
    }
  }

  // Removes the BPMN of the model. A deployment that still has running records is left in place.
  public void undeploy(String modelName) {
    for (Deployment deployment : repository.createDeploymentQuery()
        .deploymentNameLike(modelName + ".%").list()) {
      if (!deployment.getName().startsWith(modelName + ".")) {
        continue;
      }
      if (runtime.createProcessInstanceQuery().deploymentId(deployment.getId()).count() > 0) {
        log.warn("Process {} stays deployed: records still run it", deployment.getName());
        continue;
      }
      repository.deleteDeployment(deployment.getId(), false);
    }
  }
}
