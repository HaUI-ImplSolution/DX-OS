// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import HaUI.ImplSolution.dxos.core.api.app.AppModule;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

// The workspace types declared by the running app.
@Component
public class WorkspaceTypes {

  private final Map<String, WorkspaceType> byType = new LinkedHashMap<>();

  public WorkspaceTypes(ObjectProvider<AppModule> appModules) {
    appModules.orderedStream().flatMap(module -> module.workspaceTypes().stream())
        .forEach(type -> byType.put(type.type(), type));
  }

  public WorkspaceType require(String type) {
    WorkspaceType found = type == null ? null : byType.get(type);
    if (found == null) {
      throw new IllegalArgumentException(
          "Unknown workspace type: " + type + ", expected one of " + byType.keySet());
    }
    return found;
  }

  public List<WorkspaceType> all() {
    return List.copyOf(byType.values());
  }
}
