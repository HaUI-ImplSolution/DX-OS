// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.catalog;

import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.engine.data.DataService;
import HaUI.ImplSolution.dxos.engine.meta.MetaController;
import HaUI.ImplSolution.dxos.engine.meta.MetaController.EntityMeta;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta/models")
@Tag(name = "Metadata", description = "What the applied models contain, as seen by the caller")
public class ModelOpenApiController {

  private final ModelRegistry registry;
  private final DataService data;
  private final MetaController meta;
  private final UserContext user;
  private final ModelOpenApi openApi;

  public ModelOpenApiController(
      ModelRegistry registry,
      DataService data,
      MetaController meta,
      UserContext user,
      ModelOpenApi openApi) {
    this.registry = registry;
    this.data = data;
    this.meta = meta;
    this.user = user;
    this.openApi = openApi;
  }

  @GetMapping(path = "/{model}/openapi", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "The OpenAPI document of an applied model",
      description =
          "Written out from the model that runs now, so it follows every change and"
              + " rollback. It holds the entities the caller reaches (an ADMIN sees them all), with the"
              + " paths of the data API, the fields with their types, required marks and options, the"
              + " actions of each process, and the error codes. The document itself is returned, not"
              + " wrapped in the usual envelope, so a client generator or an API tool can read it"
              + " from this address. 404 when the model does not exist or the caller reaches none of"
              + " its entities.")
  public ResponseEntity<String> document(@PathVariable String model) {
    Model m = registry.require(model);
    boolean admin = user.hasRole("ADMIN");
    List<EntityMeta> entities =
        m.entities().values().stream()
            .filter(entity -> admin || data.access(m, entity).read())
            .map(entity -> meta.entity(m.name(), entity.name()).data())
            .toList();
    if (entities.isEmpty()) {
      throw new ResourceNotFoundException("Model " + model + " not found");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(openApi.toJson(openApi.build(m, entities)));
  }
}
