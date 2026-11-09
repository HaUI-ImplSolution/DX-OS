// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import java.util.ArrayList;
import java.util.List;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.impl.util.io.StringStreamSource;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.stereotype.Component;

// Puts the BPMN of the entities whose steps differ from their family into the workflow engine. An
// entity that keeps the steps of its family runs the BPMN of the family, which is always there.
// Callers run inside a transaction; the workflow engine joins it.
@Component
public class ProcessDeployer {

  private final RepositoryService repository;

  public ProcessDeployer(RepositoryService repository) {
    this.repository = repository;
  }

  // The key of the BPMN process the records of the entity run.
  public String keyOf(Model model, Entity entity) {
    return entity.process().processKey(model.name(), entity.name());
  }

  // What the workflow engine would refuse in the BPMN of the model, one line per problem, without
  // deploying anything. The list is empty when every entity runs the BPMN of its family.
  public List<String> problems(Model model) {
    List<String> problems = new ArrayList<>();
    for (Entity entity : model.entities().values()) {
      if (entity.process() == null || !entity.process().ownFlow()) {
        continue;
      }
      String key = keyOf(model, entity);
      String xml =
          ProcessBpmnGenerator.generate(key, entity.label(), entity.process().definition());
      try {
        BpmnModel bpmn =
            new BpmnXMLConverter().convertToBpmnModel(new StringStreamSource(xml), true, true);
        repository.validateProcess(bpmn).stream().filter(error -> !error.isWarning())
            .forEach(error -> problems
                .add("Entity " + entity.name() + ": " + error.getDefaultDescription()));
      } catch (RuntimeException e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
          cause = cause.getCause();
        }
        problems.add("Entity " + entity.name() + ": " + cause.getMessage());
      }
    }
    return problems;
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

  // Removes the BPMN of the model together with the process instances that run it.
  public void undeploy(String modelName) {
    for (Deployment deployment : repository.createDeploymentQuery()
        .deploymentNameLike(modelName + ".%").list()) {
      if (!deployment.getName().startsWith(modelName + ".")) {
        continue;
      }
      repository.deleteDeployment(deployment.getId(), true);
    }
  }
}
