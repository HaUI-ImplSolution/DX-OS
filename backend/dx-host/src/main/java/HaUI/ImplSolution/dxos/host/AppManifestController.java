// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host;

import HaUI.ImplSolution.dxos.core.api.app.AppMenuItem;
import HaUI.ImplSolution.dxos.core.api.app.AppModule;
import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.security.RoleDefinition;
import HaUI.ImplSolution.dxos.core.api.security.RoleProvider;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app")
@Tag(name = "App", description = "The application this backend is running")
public class AppManifestController {

  private final AppModule appModule;
  private final List<RoleDefinition> roles;

  public AppManifestController(ObjectProvider<AppModule> appModules,
      ObjectProvider<RoleProvider> roleProviders) {
    this.appModule = appModules.getIfAvailable();
    this.roles =
        roleProviders.orderedStream().flatMap(provider -> provider.roles().stream()).toList();
  }

  @GetMapping
  @Operation(
      summary = "Manifest of the running app: id, name, roles, menu, workspace types and settings",
      description = "Any signed-in user. The web client builds its navigation from the menu;"
          + " settings carry values clients use as they are, such as the GPS accuracy limit.")
  public ApiResponse<AppManifestResponse> manifest() {
    if (appModule == null) {
      throw new ResourceNotFoundException("No app is running");
    }
    return ApiResponse.success(new AppManifestResponse(appModule.id(), appModule.name(),
        roles.stream().map(RoleDefinition::name).toList(),
        appModule.menu().stream().map(AppManifestController::toMenuEntry).toList(),
        appModule.workspaceTypes().stream().map(WorkspaceType::type).toList(),
        appModule.settings()));
  }

  private static AppManifestResponse.MenuEntry toMenuEntry(AppMenuItem item) {
    return new AppManifestResponse.MenuEntry(item.id(), item.label(), item.path(),
        item.roles().stream().sorted().toList());
  }
}
