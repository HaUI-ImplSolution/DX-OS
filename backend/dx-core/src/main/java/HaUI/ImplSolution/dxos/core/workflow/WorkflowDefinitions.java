// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.workflow;

import HaUI.ImplSolution.dxos.core.api.workflow.CaseTypeDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

// The case types registered by the running app.
@Component
public class WorkflowDefinitions {

  private final Map<String, CaseTypeDefinition> byCaseType = new LinkedHashMap<>();

  public WorkflowDefinitions(ObjectProvider<CaseTypeDefinition> definitions) {
    definitions.orderedStream().forEach(definition -> {
      if (byCaseType.putIfAbsent(definition.caseType(), definition) != null) {
        throw new IllegalStateException("Case type registered twice: " + definition.caseType());
      }
    });
  }

  public CaseTypeDefinition require(String caseType) {
    CaseTypeDefinition definition = byCaseType.get(caseType);
    if (definition == null) {
      throw new IllegalArgumentException("Case type is not registered: " + caseType);
    }
    return definition;
  }

  public Optional<CaseTypeDefinition> find(String caseType) {
    return Optional.ofNullable(byCaseType.get(caseType));
  }

  public Set<String> caseTypes() {
    return Set.copyOf(byCaseType.keySet());
  }

  public boolean knowsStatus(String status) {
    return byCaseType.values().stream().anyMatch(d -> d.status(status).isPresent());
  }
}
