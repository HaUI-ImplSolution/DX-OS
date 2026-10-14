// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.app;

import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceType;
import java.util.List;
import java.util.Map;

// Declares the application the host is running. Exactly one app module is active per start.
public interface AppModule {

  // Short stable id, equal to the value of DXOS_APP.
  String id();

  // Display name shown by clients.
  String name();

  // Navigation the web client builds its menu from.
  default List<AppMenuItem> menu() {
    return List.of();
  }

  default List<WorkspaceType> workspaceTypes() {
    return List.of();
  }

  // Values clients read from the manifest instead of fixing them in their own code.
  default Map<String, String> settings() {
    return Map.of();
  }

  // Case types whose waiting requests count against a user's limit of open requests.
  default List<String> limitedCaseTypes() {
    return List.of();
  }
}
