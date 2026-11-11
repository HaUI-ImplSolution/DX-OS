// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.clients;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.engine.clients.AppClientService.AppView;
import HaUI.ImplSolution.dxos.engine.clients.AppClientService.ChangedRole;
import HaUI.ImplSolution.dxos.engine.clients.AppClientService.CreatedApp;
import HaUI.ImplSolution.dxos.engine.clients.AppClientService.NewApp;
import HaUI.ImplSolution.dxos.engine.clients.AppClientService.RenewedApp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clients")
@PreAuthorize("hasRole('ADMIN')")
@Tag(
    name = "Clients",
    description =
        "Register an outside application that calls the API with its own secret and one"
            + " role of one model; ADMIN only")
public class AppClientController {

  private final AppClientService service;

  public AppClientController(AppClientService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "The registered outside applications",
      description =
          "Each with the model and role it holds. The secret is not kept, so it is not"
              + " listed.")
  public ApiResponse<List<AppView>> list() {
    return ApiResponse.success(service.list());
  }

  @PostMapping
  @Operation(
      summary = "Register an outside application",
      description =
          "Creates a client named dxos-app-{name} that signs in with a client secret and"
              + " holds the role of the model. The answer carries the secret, and this is the only time"
              + " it is shown. 404 when the model is not applied or has no such role; 409 when the"
              + " name is taken.")
  public ResponseEntity<ApiResponse<CreatedApp>> create(@RequestBody NewApp request) {
    return noStore(service.create(request));
  }

  @PostMapping("/{name}/secret")
  @Operation(
      summary = "Give the application a new secret",
      description =
          "The old secret stops working at once. The answer carries the new secret, and"
              + " this is the only time it is shown. 404 when the application does not exist.")
  public ResponseEntity<ApiResponse<RenewedApp>> renewSecret(@PathVariable String name) {
    return noStore(service.renewSecret(name));
  }

  @PutMapping("/{name}")
  @Operation(
      summary = "Move an outside application to another role of its model",
      description =
          "Keeps the client id and the secret; the application holds the new role from"
              + " now on, and a token it got before lasts until it expires with the old role. 404 when"
              + " the application does not exist or its model has no such role.")
  public ApiResponse<AppView> changeRole(
      @PathVariable String name, @RequestBody ChangedRole request) {
    return ApiResponse.success(service.changeRole(name, request));
  }

  @DeleteMapping("/{name}")
  @Operation(
      summary = "Remove an outside application",
      description =
          "Deletes the client and its service account; the records it made stay. 404"
              + " when the application does not exist.")
  public ApiResponse<Void> remove(@PathVariable String name) {
    service.remove(name);
    return ApiResponse.success(null);
  }

  // A secret must not be kept by a cache or a proxy.
  private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
  }
}
